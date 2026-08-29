package com.odysseygen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseygen.common.BusinessException;
import com.odysseygen.common.ResultCode;
import com.odysseygen.constant.CacheConstants;
import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.dto.response.PathResponse;
import com.odysseygen.dto.response.TaskResponse;
import com.odysseygen.enums.GoalTypeEnum;
import com.odysseygen.entity.PlanRecord;
import com.odysseygen.entity.PathDetail;
import com.odysseygen.mapper.PathDetailMapper;
import com.odysseygen.mapper.PlanRecordMapper;
import com.odysseygen.service.AiGenerateTaskService;
import com.odysseygen.service.FallbackService;
import com.odysseygen.service.PlanPersistenceService;
import com.odysseygen.service.PlanService;
import com.odysseygen.util.CacheKeyUtil;
import com.odysseygen.util.DeepSeekUtil;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanServiceImpl implements PlanService {
    private final CacheKeyUtil cacheKeyUtil;
    private final PlanRecordMapper planRecordMapper;
    private final PathDetailMapper pathDetailMapper;
    private final DeepSeekUtil deepSeekUtil;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CircuitBreaker aiCircuitBreaker;
    private final AiGenerateTaskService aiGenerateTaskService;
    private final PlanPersistenceService planPersistenceService;
    private final FallbackService fallbackService;

    // ======================== 生成入口 ========================

    @Override
    public PathResponse generatePlan(Long userId, ProfileRequest request) throws Exception {
        return cacheOrLock(request, userId, () -> {
            try {
                return doGenerateAi(request);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public PathResponse generatePlanWithBreaker(Long userId, ProfileRequest request) throws Exception {
        try {
            return cacheOrLock(request, userId, () -> doGenerateAiWithBreaker(request));
        } catch (BusinessException e) {
            if (ResultCode.AI_CIRCUIT_BREAKER_OPEN.getCode().equals(e.getCode())) {
                // 熔断：AI 服务整体不可用，返回临时兜底（planId=-1 不落库，避免污染历史）
                log.warn("⚠️ 熔断降级，返回临时兜底（不落库），用户: {}", userId);
                return transientFallback(request);
            }
            if (ResultCode.AI_GENERATE_ERROR.getCode().equals(e.getCode())) {
                // AI 返回空/解析失败：兜底填充后落库（历史里保留一份完整规划）
                log.warn("⚠️ AI 数据缺失，返回规则引擎兜底（落库），用户: {}", userId);
                return fallbackPlan(userId, request);
            }
            throw e;
        }
    }

    /**
     * 缓存 + 分布式锁模板：
     * 1. 缓存命中 → 解析后直接落库（保证命中缓存的用户也能有自己的历史记录）
     * 2. 抢锁失败 → 返回繁忙
     * 3. Double-check
     * 4. 调用 AI 生成 → 先校验再写缓存 → 落库
     */
    private PathResponse cacheOrLock(ProfileRequest request, Long userId,
                                     Supplier<String> aiCallSupplier) throws Exception {
        String cacheKey = cacheKeyUtil.generateCacheKey(request);
        String lockKey = cacheKey + CacheConstants.LOCK_SUFFIX;

        // 第一处：缓存命中
        String aiResponse = (String) redisTemplate.opsForValue().get(cacheKey);
        if (aiResponse != null) {
            try {
                return persist(userId, request, aiResponse);
            } catch (Exception e) {
                log.warn("缓存内容解析失败，删除缓存并重新生成: {}", cacheKey, e);
                redisTemplate.delete(cacheKey);
            }
        }

        // 第二处：抢分布式锁（SETNX）
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", CacheConstants.LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            // 相同画像的另一个用户正在生成（AI 调用约 20-40s），提示稍后重试
            throw new BusinessException("系统繁忙：相同画像的规划正在生成中，请约 20-40 秒后重试");
        }

        try {
            // 第三处：Double-check
            aiResponse = (String) redisTemplate.opsForValue().get(cacheKey);
            if (aiResponse != null) {
                try {
                    return persist(userId, request, aiResponse);
                } catch (Exception e) {
                    log.warn("Double-check 缓存解析失败，删除缓存并重新生成: {}", cacheKey, e);
                    redisTemplate.delete(cacheKey);
                }
            }

            // 第四处：调用 AI 生成
            log.info("⏳ 缓存未命中，开始 AI 生成... 画像: {}, 用户: {}", request.getMajor(), userId);
            aiResponse = aiCallSupplier.get();
            List<Map<String, Object>> paths = parsePaths(aiResponse);

            // 先落库（DB 是数据源），成功后再写缓存，避免 DB 失败留下脏缓存
            Long planId = planPersistenceService.savePlan(userId, request, paths);
            PathResponse response = planPersistenceService.buildResponse(planId, paths, request);

            try {
                redisTemplate.opsForValue().set(cacheKey, aiResponse, CacheConstants.CACHE_TTL);
                log.info("✅ AI 结果缓存写入成功！Key: {}", cacheKey);
            } catch (Exception e) {
                log.warn("缓存写入失败，不影响主流程", e);
            }

            return response;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /** 解析 AI 返回的 paths 数组，非法/为空时抛异常 */
    private List<Map<String, Object>> parsePaths(String aiResponse) throws Exception {
        Map<String, Object> result = objectMapper.readValue(aiResponse, new TypeReference<>() {});
        Object pathsObj = result.get("paths");
        if (!(pathsObj instanceof List)) {
            log.error("AI 返回的 paths 不是数组，原始响应: {}", aiResponse);
            throw new BusinessException(ResultCode.AI_GENERATE_ERROR);
        }
        List<Map<String, Object>> paths = (List<Map<String, Object>>) pathsObj;
        if (paths.isEmpty()) {
            log.error("AI 返回的路径为空，原始响应: {}", aiResponse);
            throw new BusinessException(ResultCode.AI_GENERATE_ERROR);
        }
        return paths;
    }

    /** 解析 AI 响应并落库 */
    private PathResponse persist(Long userId, ProfileRequest request, String aiResponse) throws Exception {
        List<Map<String, Object>> paths = parsePaths(aiResponse);
        Long planId = planPersistenceService.savePlan(userId, request, paths);
        return planPersistenceService.buildResponse(planId, paths, request);
    }

    private String doGenerateAi(ProfileRequest request) throws Exception {
        String profileJson = buildProfileJson(request);
        return deepSeekUtil.generatePaths(
                request.getGoalType(),
                profileJson,
                request.getGraduationYear(),
                request.getGoalData()
        );
    }

    private String buildProfileJson(ProfileRequest request) throws Exception {
        Map<String, Object> profileData = new LinkedHashMap<>();
        profileData.put("userId", "current");
        profileData.put("goalType", request.getGoalType());
        profileData.put("major", request.getMajor());
        profileData.put("gpa", request.getGpa());
        profileData.put("schoolLevel", request.getSchoolLevel());
        profileData.put("englishLevel", request.getEnglishLevel());
        profileData.put("isPartyMember", request.getIsPartyMember());
        profileData.put("graduationYear", request.getGraduationYear());
        profileData.put("goalData", request.getGoalData());
        profileData.put("personalityTags", request.getPersonalityTags());
        return objectMapper.writeValueAsString(profileData);
    }

    private String doGenerateAiWithBreaker(ProfileRequest request) {
        try {
            return aiCircuitBreaker.executeSupplier(() -> {
                try {
                    return doGenerateAi(request);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (CallNotPermittedException e) {
            log.warn("🚫 熔断器已打开，AI 服务不可用");
            throw new BusinessException(ResultCode.AI_CIRCUIT_BREAKER_OPEN);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CallNotPermittedException) {
                log.warn("🚫 熔断器已打开（嵌套异常），AI 服务不可用");
                throw new BusinessException(ResultCode.AI_CIRCUIT_BREAKER_OPEN);
            }
            log.error("❌ AI 调用失败: {}", cause != null ? cause.getMessage() : e.getMessage());
            throw new BusinessException(ResultCode.AI_GENERATE_ERROR);
        }
    }

    // ======================== 兜底方案（规则引擎驱动） ========================

    /** 熔断临时兜底：AI 服务不可用，返回临时兜底（planId=-1，不落库） */
    private PathResponse transientFallback(ProfileRequest request) {
        try {
            List<Map<String, Object>> paths = fallbackService.generatePaths(request);
            return planPersistenceService.buildResponse(-1L, paths, request);
        } catch (Exception e) {
            log.error("临时兜底生成失败", e);
            throw new BusinessException(ResultCode.AI_GENERATE_ERROR);
        }
    }

    /** 数据缺失兜底：AI 返回空/解析失败，兜底填充后落库（历史里保留完整规划） */
    private PathResponse fallbackPlan(Long userId, ProfileRequest request) {
        try {
            List<Map<String, Object>> paths = fallbackService.generatePaths(request);
            Long planId = planPersistenceService.savePlan(userId, request, paths);
            return planPersistenceService.buildResponse(planId, paths, request);
        } catch (Exception e) {
            log.error("兜底方案生成失败", e);
            throw new BusinessException(ResultCode.AI_GENERATE_ERROR);
        }
    }

    // ======================== 异步任务 ========================

    @Override
    public String generatePlanAsyncWithBreaker(Long userId, ProfileRequest request) {
        String taskId = UUID.randomUUID().toString().replace("-", "");

        TaskResponse task = new TaskResponse();
        task.setTaskId(taskId);
        task.setStatus("PENDING");
        task.setCreatedAt(System.currentTimeMillis());

        String redisKey = CacheConstants.TASK_PREFIX + taskId;
        try {
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(task), CacheConstants.TASK_TTL);
        } catch (Exception e) {
            log.error("初始化任务状态失败", e);
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        // 通过独立 Bean 触发异步执行，避免同类自调用导致 @Async 失效
        aiGenerateTaskService.executeAsync(userId, request, taskId);
        return taskId;
    }

    @Override
    public TaskResponse getTaskStatus(String taskId) throws Exception {
        String redisKey = CacheConstants.TASK_PREFIX + taskId;
        String json = (String) redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            throw new BusinessException(404, "任务不存在或已过期");
        }
        return objectMapper.readValue(json, TaskResponse.class);
    }

    // ======================== 查询 / 管理 ========================

    @Override
    public PathResponse getPlanDetail(Long planId, Long userId) throws Exception {
        // 权限校验：确保该规划属于当前用户
        PlanRecord record = planRecordMapper.selectById(planId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权限查看该规划");
        }

        LambdaQueryWrapper<PathDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PathDetail::getPlanId, planId)
                .orderByAsc(PathDetail::getSortOrder);
        List<PathDetail> details = pathDetailMapper.selectList(wrapper);

        if (details.isEmpty()) {
            throw new BusinessException(ResultCode.PLAN_NOT_EXIST);
        }

        PathResponse response = new PathResponse();
        response.setPlanId(planId);
        List<PathResponse.PathItem> items = new ArrayList<>();
        for (PathDetail d : details) {
            PathResponse.PathItem item = new PathResponse.PathItem();
            item.setPathType(d.getPathType());
            item.setPathName(d.getPathName());
            item.setPathSummary(d.getPathSummary());
            item.setDescription(d.getDescription());
            item.setTimeline(objectMapper.readValue(d.getTimeline(), new TypeReference<>() {}));
            item.setKeyNodes(objectMapper.readValue(d.getKeyNodes(), new TypeReference<>() {}));
            item.setSkillGap(objectMapper.readValue(d.getSkillGap(), new TypeReference<>() {}));
            if (d.getSalaryExpectation() != null && !d.getSalaryExpectation().isEmpty()) {
                item.setSalaryExpectation(objectMapper.readValue(d.getSalaryExpectation(), new TypeReference<PathResponse.SalaryExpectation>() {}));
            }
            if (d.getStopLossAdvice() != null && !d.getStopLossAdvice().isEmpty()) {
                item.setStopLossAdvice(objectMapper.readValue(
                        d.getStopLossAdvice(),
                        new TypeReference<PathResponse.StopLossAdvice>() {}
                ));
            }
            item.setRiskFactors(objectMapper.readValue(d.getRiskFactors(), new TypeReference<>() {}));
            item.setRecommendedActions(objectMapper.readValue(d.getRecommendedActions(), new TypeReference<>() {}));
            items.add(item);
        }
        response.setPaths(items);
        return response;
    }

    @Override
    public void toggleFavorite(Long planId, Long userId) {
        // 单条原子 UPDATE，消除 Read-Modify-Write 竞态，同时省去一次查询
        LambdaUpdateWrapper<PlanRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PlanRecord::getPlanId, planId)
                .eq(PlanRecord::getUserId, userId)
                .setSql("is_favorite = NOT is_favorite")
                .set(PlanRecord::getUpdatedAt, LocalDateTime.now());
        int updated = planRecordMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BusinessException(ResultCode.PLAN_NOT_EXIST);
        }
    }

    @Override
    public void deletePlan(Long planId, Long userId) {
        // 单条 SQL：userId 条件同时承担权限校验；@TableLogic 自动转为逻辑删除
        LambdaQueryWrapper<PlanRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlanRecord::getPlanId, planId)
                .eq(PlanRecord::getUserId, userId);
        int deleted = planRecordMapper.delete(wrapper);
        if (deleted == 0) {
            throw new BusinessException(ResultCode.PLAN_NOT_EXIST);
        }
    }

    @Override
    public List<PlanRecord> getPlanRecordsByUserId(Long userId) {
        LambdaQueryWrapper<PlanRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlanRecord::getUserId, userId)
                .eq(PlanRecord::getIsDeleted, false)
                .orderByDesc(PlanRecord::getCreatedAt);
        return planRecordMapper.selectList(wrapper);
    }

    @Override
    public IPage<PlanRecord> getPlanRecordsByUserId(Long userId, Integer page, Integer size) {
        Page<PlanRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PlanRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlanRecord::getUserId, userId)
                .eq(PlanRecord::getIsDeleted, false)
                .orderByDesc(PlanRecord::getCreatedAt);
        IPage<PlanRecord> recordPage = planRecordMapper.selectPage(pageParam, wrapper);

        // 一次批量查询所有 path_details，避免 N+1
        List<PlanRecord> records = recordPage.getRecords();
        if (!records.isEmpty()) {
            List<Long> planIds = records.stream()
                    .map(PlanRecord::getPlanId)
                    .collect(Collectors.toList());
            LambdaQueryWrapper<PathDetail> pathWrapper = new LambdaQueryWrapper<>();
            pathWrapper.in(PathDetail::getPlanId, planIds)
                    .orderByAsc(PathDetail::getSortOrder)
                    .select(PathDetail::getPlanId, PathDetail::getPathName);
            List<PathDetail> details = pathDetailMapper.selectList(pathWrapper);

            Map<Long, List<String>> pathNamesMap = details.stream()
                    .collect(Collectors.groupingBy(
                            PathDetail::getPlanId,
                            Collectors.mapping(PathDetail::getPathName, Collectors.toList())));

            for (PlanRecord record : records) {
                record.setPathNames(pathNamesMap.getOrDefault(record.getPlanId(), Collections.emptyList()));
            }
        }
        return recordPage;
    }

    @Override
    public Map<Integer, List<PathResponse.PathItem>> compareThreeGoals(Long userId) {
        Map<Integer, List<PathResponse.PathItem>> result = new LinkedHashMap<>();

        // 1. 获取用户所有规划记录（已填充 goalType）
        List<PlanRecord> allRecords = getPlanRecordsByUserId(userId);

        // 2. 按 goalType 分组，取每组最新的一条
        Map<Integer, PlanRecord> latestByGoal = new HashMap<>();
        for (PlanRecord record : allRecords) {
            Integer goalType = record.getGoalType();
            if (GoalTypeEnum.fromCode(goalType) == null) continue;
            PlanRecord existing = latestByGoal.get(goalType);
            if (existing == null || record.getCreatedAt().isAfter(existing.getCreatedAt())) {
                latestByGoal.put(goalType, record);
            }
        }

        // 3. 对每种目标获取路径详情（传入 userId 校验权限）
        for (GoalTypeEnum goalTypeEnum : GoalTypeEnum.values()) {
            Integer goalType = goalTypeEnum.getCode();
            PlanRecord latest = latestByGoal.get(goalType);
            if (latest != null) {
                try {
                    PathResponse detail = getPlanDetail(latest.getPlanId(), userId);
                    result.put(goalType, detail.getPaths());
                } catch (Exception e) {
                    log.warn("获取目标 {} 的路径详情失败: {}", goalType, e.getMessage());
                    result.put(goalType, Collections.emptyList());
                }
            } else {
                result.put(goalType, Collections.emptyList());
            }
        }

        return result;
    }
}
