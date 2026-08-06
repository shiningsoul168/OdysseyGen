package com.odysseygen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.odysseygen.constant.CacheConstants;
import com.odysseygen.dto.response.TaskResponse;
import com.odysseygen.service.PlanService;
import com.odysseygen.service.rule.impl.SalaryRuleEngine;
import com.odysseygen.util.CacheKeyUtil;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.odysseygen.common.BusinessException;
import com.odysseygen.common.ResultCode;
import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.dto.response.PathResponse;
import com.odysseygen.entity.*;
import com.odysseygen.mapper.*;
import com.odysseygen.util.DeepSeekUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanServiceImpl implements PlanService {
    private final CacheKeyUtil cacheKeyUtil;
    private final UserProfileMapper userProfileMapper;
    private final PlanRecordMapper planRecordMapper;
    private final PathDetailMapper pathDetailMapper;
    private final DeepSeekUtil deepSeekUtil;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String TASK_PREFIX = "plan:task:";
    private static final long TASK_TTL_MINUTES = 5;
    private final CircuitBreaker aiCircuitBreaker;
    private final SalaryRuleEngine salaryRuleEngine;

    // ======================== 公共模板方法 ========================

    private PathResponse cacheOrLock(ProfileRequest request, Long userId,
                                     Supplier<String> aiCallSupplier) throws Exception {
        String cacheKey = cacheKeyUtil.generateCacheKey(request);
        String lockKey = cacheKey + CacheConstants.LOCK_SUFFIX;

        // 第一处：缓存命中
        String aiResponse = (String) redisTemplate.opsForValue().get(cacheKey);
        if (aiResponse != null) {
            log.info("🎯 AI 结果缓存命中！画像: {}, 用户: {}", request.getMajor(), userId);
            PathResponse response = buildPathResponse(aiResponse, request);
            fillPlanId(response, userId);  // ✅ 补齐 planId
            return response;
        }

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", CacheConstants.LOCK_TTL);

        if (locked != null && locked) {
            try {
                // 第二处：Double-check 缓存命中
                aiResponse = (String) redisTemplate.opsForValue().get(cacheKey);
                if (aiResponse != null) {
                    log.info("🎯 Double-check 缓存命中！画像: {}, 用户: {}", request.getMajor(), userId);
                    PathResponse response = buildPathResponse(aiResponse, request);
                    fillPlanId(response, userId);  // ✅ 补齐 planId
                    return response;
                }

                // ... 后续生成逻辑保持不变 ...
                log.info("⏳ 缓存未命中，开始 AI 生成... 画像: {}, 用户: {}", request.getMajor(), userId);
                aiResponse = aiCallSupplier.get();

                try {
                    redisTemplate.opsForValue().set(cacheKey, aiResponse, CacheConstants.CACHE_TTL);
                    log.info("✅ AI 结果缓存写入成功！Key: {}", cacheKey);
                } catch (Exception e) {
                    log.warn("缓存写入失败，不影响主流程", e);
                }

                Map<String, Object> result = objectMapper.readValue(aiResponse, new TypeReference<>() {});
                List<Map<String, Object>> paths = (List<Map<String, Object>>) result.get("paths");
                if (paths == null || paths.isEmpty()) {
                    log.error("AI 返回的路径为空，原始响应: {}", aiResponse);
                    throw new BusinessException(ResultCode.AI_GENERATE_ERROR);
                }
                return savePlanAndBuildResponse(userId, request, paths);

            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            log.info("⏳ 其他请求正在生成中，当前请求直接返回繁忙，用户: {}", userId);
            throw new BusinessException("系统繁忙，请稍后重试");
        }
    }

    // ====== 新增辅助方法：从数据库补齐 planId ======
    private void fillPlanId(PathResponse response, Long userId) {
        if (response.getPlanId() != null && response.getPlanId() > 0) {
            return;  // 已经有 planId 了，不需要查
        }
        try {
            LambdaQueryWrapper<PlanRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PlanRecord::getUserId, userId)
                    .eq(PlanRecord::getIsDeleted, false)
                    .orderByDesc(PlanRecord::getCreatedAt)
                    .last("LIMIT 1");
            PlanRecord record = planRecordMapper.selectOne(wrapper);
            if (record != null) {
                response.setPlanId(record.getPlanId());
                log.info("✅ 缓存响应补充 planId 成功: {}", record.getPlanId());
            } else {
                log.warn("⚠️ 未找到该用户的规划记录，无法补充 planId");
            }
        } catch (Exception e) {
            log.warn("补充 planId 失败", e);
        }
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

    private PathResponse buildPathResponse(String aiResponse, ProfileRequest request) throws Exception {
        // 先打印原始响应，方便排查
        log.info("AI 原始响应（前500字符）: {}", aiResponse.length() > 500 ? aiResponse.substring(0, 500) + "..." : aiResponse);

        Map<String, Object> result;
        try {
            result = objectMapper.readValue(aiResponse, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("AI 响应 JSON 解析失败，原始响应: {}", aiResponse, e);
            // 解析失败时返回降级方案
            return buildFallbackPlan(request);
        }

        // 检查 paths 是否存在且为数组
        Object pathsObj = result.get("paths");
        if (pathsObj == null) {
            log.error("AI 响应缺少 paths 字段，原始响应: {}", aiResponse);
            return buildFallbackPlan(request);
        }

        List<Map<String, Object>> paths;
        try {
            if (pathsObj instanceof List) {
                paths = (List<Map<String, Object>>) pathsObj;
            } else {
                log.error("AI 返回的 paths 不是数组，实际类型: {}", pathsObj.getClass().getName());
                return buildFallbackPlan(request);
            }
        } catch (Exception e) {
            log.error("AI 返回的 paths 格式异常", e);
            return buildFallbackPlan(request);
        }

        if (paths == null || paths.isEmpty()) {
            log.error("AI 返回的路径为空，原始响应: {}", aiResponse);
            return buildFallbackPlan(request);
        }

        // ✅ 解析每条路径
        List<PathResponse.PathItem> pathItems = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            Map<String, Object> p = paths.get(i);

            PathResponse.PathItem item = new PathResponse.PathItem();
            item.setPathType((Integer) p.getOrDefault("pathType", i + 1));
            item.setPathName((String) p.get("pathName"));
            item.setPathSummary((String) p.get("pathSummary"));
            item.setDescription((String) p.get("description"));
            item.setTimeline((List<Map<String, String>>) p.get("timeline"));
            item.setKeyNodes((List<Map<String, String>>) p.get("keyNodes"));
            item.setSkillGap((List<String>) p.get("skillGap"));

            // 处理薪资
            Object salaryObj = p.get("salaryExpectation");
            if (salaryObj != null) {
                item.setSalaryExpectation(objectMapper.convertValue(salaryObj, PathResponse.SalaryExpectation.class));
            } else {
                Map<String, Integer> salaryMap = calculateSmartSalary(request.getGoalType(), item.getPathType(), request);
                PathResponse.SalaryExpectation salary = new PathResponse.SalaryExpectation();
                salary.setEntry(salaryMap.get("entry"));
                salary.setMid(salaryMap.get("mid"));
                salary.setSenior(salaryMap.get("senior"));
                item.setSalaryExpectation(salary);
                log.info("⚠️ AI 未返回 salaryExpectation，使用规则引擎兜底: pathType={}, salary={}", item.getPathType(), salaryMap);
            }

            // 处理 stopLossAdvice
            Object stopLossObj = p.get("stopLossAdvice");
            if (stopLossObj != null) {
                item.setStopLossAdvice(objectMapper.convertValue(stopLossObj, PathResponse.StopLossAdvice.class));
            }

            item.setRiskFactors((List<String>) p.get("riskFactors"));
            item.setRecommendedActions((List<String>) p.get("recommendedActions"));
            pathItems.add(item);
        }

        PathResponse response = new PathResponse();
        response.setPaths(pathItems);
        return response;
    }

    // ======================== 接口实现 ========================

    @Override
    @Transactional
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
    @Transactional(rollbackFor = Exception.class)  // ✅ 新增事务
    public PathResponse generatePlanWithBreaker(Long userId, ProfileRequest request) throws Exception {
        try {
            return cacheOrLock(request, userId, () -> doGenerateAiWithBreaker(request));
        } catch (BusinessException e) {
            if (ResultCode.AI_CIRCUIT_BREAKER_OPEN.getCode().equals(e.getCode())) {
                log.warn("⚠️ 熔断降级，返回兜底方案，用户: {}", userId);
                return buildFallbackPlan(request);
            }
            throw e;
        }
    }

    private PathResponse buildFallbackPlan(ProfileRequest request) {
        Integer goalType = request.getGoalType();

        // ✅ 根据目标类型生成三条差异化的备选路径
        List<PathResponse.PathItem> fallbackPaths = new ArrayList<>();

        // 路径1：主流路径（最推荐的通用方案）
        fallbackPaths.add(buildFallbackItem(goalType, 1));
        // 路径2：备用路径（备选方案）
        fallbackPaths.add(buildFallbackItem(goalType, 2));
        // 路径3：冲刺路径（理想方案）
        fallbackPaths.add(buildFallbackItem(goalType, 3));

        PathResponse response = new PathResponse();
        response.setPaths(fallbackPaths);
        response.setPlanId(-1L);
        return response;
    }

    private PathResponse.PathItem buildFallbackItem(Integer goalType, int pathType) {
        // ====== 根据目标类型 + 路径类型，生成差异化内容 ======
        String pathName, pathSummary, description;
        List<Map<String, String>> timeline;
        List<Map<String, String>> keyNodes;
        List<String> skillGap;
        List<String> riskFactors;
        List<String> recommendedActions;
        Integer entrySalary, midSalary, seniorSalary;

        // ====== 就业方向 ======
        if (goalType == 1) {
            switch (pathType) {
                case 1:
                    pathName = "💼 快速就业路线（研发岗）";
                    pathSummary = "以本科学历直接冲击中小企业研发岗，快速积累实战经验";
                    description = "专注于构建可落地的项目经验，通过 GitHub 开源项目和实习经历弥补学历短板。适合动手能力强、不排斥编码、希望快速经济独立的人群。";
                    timeline = List.of(
                            Map.of("year", "当前", "action", "梳理已学知识，搭建个人技术博客，用 Spring Boot + Vue 写一个完整项目并部署"),
                            Map.of("year", "3 个月后", "action", "完成第一个项目并上传 GitHub，简历上能有 2-3 个可展示的项目链接"),
                            Map.of("year", "6 个月后", "action", "投递中小厂研发岗位，针对性刷题和八股文，参加校园招聘和线上招聘会")
                    );
                    keyNodes = List.of(
                            Map.of("node", "完成一个全栈项目并部署到公网", "deadline", "3 个月内"),
                            Map.of("node", "整理简历并投递 50+ 家中小厂", "deadline", "6 个月内")
                    );
                    skillGap = List.of("Spring Boot / MyBatis", "MySQL 索引优化", "Linux 基础运维", "Git 协作规范");
                    riskFactors = List.of("学历在简历筛选中处于劣势", "无实习经历，需要靠项目弥补");
                    recommendedActions = List.of(
                            "去 B站/慕课网找完整项目教程，跟做完并自己改一遍",
                            "把项目部署到云服务器（学生优惠），简历里写访问链接",
                            "整理 GitHub，让自己的代码看起来规范（有 README、有注释）",
                            "刷 LeetCode Hot 100 前 50 题 + 背 Java 八股文"
                    );
                    entrySalary = 10;
                    midSalary = 20;
                    seniorSalary = 35;
                    break;
                case 2:
                    pathName = "🛠️ 备选路线（运维/实施/技术支持）";
                    pathSummary = "从运维或技术支持切入，积累行业经验后再转岗或跳槽";
                    description = "研发岗竞争激烈时，可考虑运维工程师、实施工程师等岗位，技术要求相对低，更容易获得面试机会。积累 1-2 年经验后再规划转岗。";
                    timeline = List.of(
                            Map.of("year", "当前", "action", "学习 Linux 基础命令、Shell 脚本、网络基础"),
                            Map.of("year", "3 个月后", "action", "熟悉常见运维工具（Docker、Nginx、MySQL 运维），完成一个运维部署项目"),
                            Map.of("year", "6 个月后", "action", "投递运维/技术支持岗位，重点投递传统行业数字化转型的企业")
                    );
                    keyNodes = List.of(
                            Map.of("node", "掌握 Linux + Docker 基本操作", "deadline", "3 个月内"),
                            Map.of("node", "拿到一个运维或技术支持 Offer", "deadline", "6 个月内")
                    );
                    skillGap = List.of("Linux 运维", "Shell/Python 脚本", "网络基础 (TCP/IP)", "Docker 容器化");
                    riskFactors = List.of("运维岗位天花板较低，后期需转岗或深耕 DevOps", "薪资涨幅有限");
                    recommendedActions = List.of(
                            "学习 Linux 常用命令（能完成文件操作、进程管理、日志查看）",
                            "安装 Docker，学会写 Dockerfile 和 docker-compose.yml",
                            "了解云服务（阿里云/腾讯云）的基础产品，能用 ECS + RDS 部署项目",
                            "考一个云厂商的基础认证（阿里云 ACA），简历上加分"
                    );
                    entrySalary = 7;
                    midSalary = 15;
                    seniorSalary = 28;
                    break;
                default: // case 3
                    pathName = "🚀 冲刺路线（外包/自由职业 → 积累经验）";
                    pathSummary = "通过外包、自由职业积累项目经验，以实战能力打开局面";
                    description = "如果校招投递效果不理想，通过接外包项目、参与开源贡献等方式证明自己的实战能力，积累 1 年后以经验跳槽到更好的平台。";
                    timeline = List.of(
                            Map.of("year", "当前", "action", "注册国内外包平台（程序员客栈、开源中国众包），接小需求练手"),
                            Map.of("year", "3 个月后", "action", "完成 3-5 个小外包项目，积累客户好评和真实案例"),
                            Map.of("year", "6 个月后", "action", "以外包项目经验包装简历，重新投递正式岗位，或继续深耕外包赛道，往独立开发者方向转型")
                    );
                    keyNodes = List.of(
                            Map.of("node", "完成第一个外包项目并获得好评", "deadline", "1 个月内"),
                            Map.of("node", "累计完成 5 个项目，形成案例集", "deadline", "4 个月内")
                    );
                    skillGap = List.of("项目沟通与需求理解", "快速学习新框架的能力", "独立解决问题");
                    riskFactors = List.of("外包收入不稳定", "缺乏团队协作经验，后期入职需要适应");
                    recommendedActions = List.of(
                            "在程序员客栈注册，完善技能标签，主动报价接小需求",
                            "把每个外包项目都写成案例，附上交付物截图和客户评价",
                            "每天花 2 小时学新技术，保持竞争力",
                            "参与知名开源项目（如 Spring 生态、Vue 生态），哪怕只修一个小 Bug 也是亮点"
                    );
                    entrySalary = 5;
                    midSalary = 18;
                    seniorSalary = 35;
                    break;
            }

            // ====== 考研方向 ======
        } else if (goalType == 2) {
            switch (pathType) {
                case 1:
                    pathName = "📚 考研备选路线（冲刺名校）";
                    pathSummary = "以本科学历报考 211/985 院校，目标学术深造";
                    description = "适合有学术追求、愿意投入时间备考的用户。建议从现在开始制定复习计划，选择目标院校和专业，全力以赴准备初试。";
                    timeline = List.of(
                            Map.of("year", "当前", "action", "确定目标院校和专业，购买教材和历年真题，制定阶段复习计划"),
                            Map.of("year", "3 个月后", "action", "完成第一轮基础复习，英语词汇量达到 5000+，数学完成一轮刷题"),
                            Map.of("year", "6 个月后", "action", "进入第二轮强化，专业课专项突破，参加目标院校的暑期营或联系导师")
                    );
                    keyNodes = List.of(
                            Map.of("node", "确定目标院校并完成信息收集", "deadline", "1 个月内"),
                            Map.of("node", "完成第一轮复习", "deadline", "3 个月内"),
                            Map.of("node", "模拟考达到目标分数 80%", "deadline", "考前 2 个月")
                    );
                    skillGap = List.of("高等数学基础", "英语长难句阅读", "专业课知识体系", "考研政治时政");
                    riskFactors = List.of("考研竞争激烈，分数线逐年上涨", "心理压力大，容易产生畏难情绪");
                    recommendedActions = List.of(
                            "每天固定时间学习（早起 2 小时 + 晚上 3 小时）",
                            "加入考研群，与同专业考生交流，获取内部资料",
                            "每周末做一次模拟考，记录分数变化",
                            "提前联系目标院校的学长学姐获取信息"
                    );
                    entrySalary = 0;
                    midSalary = 0;
                    seniorSalary = 0;
                    break;
                case 2:
                    pathName = "🏫 备选路线（保底院校）";
                    pathSummary = "选择竞争较小的院校，稳妥上岸为首要目标";
                    description = "如果名校竞争过于激烈，可以选择双非院校的强势专业，以稳妥上岸为首要目标，未来再通过读博或工作弥补学历差距。";
                    timeline = List.of(
                            Map.of("year", "当前", "action", "筛选 3-5 个双非但专业实力不错的院校"),
                            Map.of("year", "3 个月后", "action", "针对性复习目标院校的自命题专业课"),
                            Map.of("year", "考前", "action", "优先确保过线，调剂时可作为保底选择")
                    );
                    keyNodes = List.of(
                            Map.of("node", "确定 3 个保底目标院校", "deadline", "1 个月内"),
                            Map.of("node", "获得院校自命题资料和往年真题", "deadline", "2 个月内")
                    );
                    skillGap = List.of("信息搜集能力", "专业课重点把握", "调剂技巧");
                    riskFactors = List.of("双非院校学历认可度较低", "部分院校就业资源有限");
                    recommendedActions = List.of(
                            "关注研招网和目标院校官网，及时获取招生简章变化",
                            "准备调剂备选方案，提前了解调剂流程"
                    );
                    entrySalary = 0;
                    midSalary = 0;
                    seniorSalary = 0;
                    break;
                default: // case 3
                    pathName = "💼 考研 + 就业双备选";
                    pathSummary = "一边考研一边关注招聘，两条腿走路";
                    description = "考研不是唯一出路，建议同步关注秋招和春招，如果考研结果不理想，依然有就业备选方案，避免毕业即失业。";
                    timeline = List.of(
                            Map.of("year", "当前", "action", "复习考研的同时，花 20% 精力关注招聘信息，投递部分保底岗位"),
                            Map.of("year", "3 个月后", "action", "参加秋招，拿一个保底 Offer（优先级：国企 > 私企）"),
                            Map.of("year", "考研结束后", "action", "根据考研结果决定：上岸则入学，失败则入职保底岗位")
                    );
                    keyNodes = List.of(
                            Map.of("node", "获得一个保底就业 Offer", "deadline", "秋招结束前"),
                            Map.of("node", "考研初试过线", "deadline", "次年 3 月")
                    );
                    skillGap = List.of("时间管理能力", "多任务并行处理能力");
                    riskFactors = List.of("精力分散可能导致考研和就业两头落空", "需要极强的自律能力");
                    recommendedActions = List.of(
                            "周一到周五专注考研，周末投递简历和刷面试题",
                            "精准投递，只投递与自己专业匹配的岗位",
                            "不盲目追求大厂，以中小厂保底为主"
                    );
                    entrySalary = 0;
                    midSalary = 0;
                    seniorSalary = 0;
                    break;
            }

            // ====== 考公方向 ======
        } else {
            switch (pathType) {
                case 1:
                    pathName = "🏛️ 考公路线（国考/省考）";
                    pathSummary = "以国考和省考为目标，备考行测和申论";
                    description = "适合追求稳定、愿意投入时间备考公务员的用户。建议根据自身专业情况选择岗位，制定系统的备考计划，关注每年考试时间节点。";
                    timeline = List.of(
                            Map.of("year", "当前", "action", "了解国考/省考报名条件，确定意向岗位类型，购买教材和题库"),
                            Map.of("year", "3 个月后", "action", "完成行测基础训练，各题型正确率达到 60% 以上，开始申论大作文练习"),
                            Map.of("year", "考前 1 个月", "action", "进入冲刺阶段，每周至少做 3 套真题，参加模拟考试")
                    );
                    keyNodes = List.of(
                            Map.of("node", "确定报考岗位并完成报名", "deadline", "国考报名截止前"),
                            Map.of("node", "行测模考稳定 65 分以上", "deadline", "考前 1 个月")
                    );
                    skillGap = List.of("行测题型熟练度", "申论写作规范", "政策热点敏感度", "面试表达能力");
                    riskFactors = List.of("竞争比例极高，部分岗位甚至达到 1000:1", "需要至少 6 个月以上的持续备考");
                    recommendedActions = List.of(
                            "报名参加线下或线上考公培训班，跟随系统学习",
                            "每天刷题 100 道行测题 + 一篇申论作文",
                            "关注人民日报、半月谈等官方媒体，积累素材",
                            "参加粉笔等平台的模拟考试，了解自己的排名"
                    );
                    entrySalary = 0;
                    midSalary = 0;
                    seniorSalary = 0;
                    break;
                case 2:
                    pathName = "📋 备选路线（事业单位/国企）";
                    pathSummary = "关注事业单位招聘和国企招聘，多一条路";
                    description = "如果国考/省考竞争过于激烈，事业单位和国企的招聘难度相对较低，且福利待遇同样稳定，可以同步关注。";
                    timeline = List.of(
                            Map.of("year", "当前", "action", "关注事业单位招聘信息（省人社厅网站、事业单位招聘考试网）"),
                            Map.of("year", "考试前", "action", "根据事业单位考试内容（公共基础知识/职测）进行专项备考"),
                            Map.of("year", "毕业后", "action", "如果公务员未录取，优先入职事业单位/国企，再规划继续备考")
                    );
                    keyNodes = List.of(
                            Map.of("node", "确定 3-5 个事业编/国企意向岗位", "deadline", "招聘公告发布后"),
                            Map.of("node", "完成至少一个单位的笔面试", "deadline", "毕业后 3 个月内")
                    );
                    skillGap = List.of("事业单位考情了解", "结构化面试技巧");
                    riskFactors = List.of("事业单位薪资涨幅有限", "部分国企基层岗位工作强度不低");
                    recommendedActions = List.of(
                            "关注当地人才引进政策，部分城市对本科生有补贴",
                            "报名事业单位培训班，内容与考公重叠度高"
                    );
                    entrySalary = 0;
                    midSalary = 0;
                    seniorSalary = 0;
                    break;
                default: // case 3
                    pathName = "🌱 基层项目 + 曲线入编";
                    pathSummary = "通过三支一扶、西部计划等基层项目入编";
                    description = "如果公务员和事业单位连续 2 年未成功，可以考虑三支一扶、西部计划等基层项目，服务期满后可通过定向招录进入公务员或事业单位编制。";
                    timeline = List.of(
                            Map.of("year", "当前", "action", "了解三支一扶、西部计划、特岗教师等基层项目的报名条件和政策"),
                            Map.of("year", "毕业后", "action", "报名参加基层项目，服务期通常为 2-3 年"),
                            Map.of("year", "服务期满后", "action", "参加定向招录考试，或者利用基层经验参加考公/考编面试")
                    );
                    keyNodes = List.of(
                            Map.of("node", "确定意向基层项目", "deadline", "毕业前"),
                            Map.of("node", "完成基层项目报名并录用", "deadline", "毕业后 1 年内")
                    );
                    skillGap = List.of("基层工作能力", "吃苦耐劳的意志力");
                    riskFactors = List.of("基层项目工作环境艰苦", "部分地区待遇偏低", "需要 2-3 年时间成本");
                    recommendedActions = List.of(
                            "提前了解各省基层项目的招募公告",
                            "加入基层项目交流群，了解真实的基层工作状态",
                            "做好吃苦和长期扎根的准备"
                    );
                    entrySalary = 0;
                    midSalary = 0;
                    seniorSalary = 0;
                    break;
            }
        }

        // ====== 组装 PathItem ======
        PathResponse.PathItem item = new PathResponse.PathItem();
        item.setPathType(pathType);
        item.setPathName(pathName);
        item.setPathSummary(pathSummary);
        item.setDescription(description);
        item.setTimeline(timeline);
        item.setKeyNodes(keyNodes);
        item.setSkillGap(skillGap);
        item.setRiskFactors(riskFactors);
        item.setRecommendedActions(recommendedActions);

        PathResponse.SalaryExpectation salary = new PathResponse.SalaryExpectation();
        salary.setEntry(entrySalary);
        salary.setMid(midSalary);
        salary.setSenior(seniorSalary);
        item.setSalaryExpectation(salary);

        // ✅ 保留止损建议
        PathResponse.StopLossAdvice stopLoss = new PathResponse.StopLossAdvice();
        stopLoss.setTrigger("如果该方案在 6 个月内没有进展");
        stopLoss.setAction("重新评估职业规划，尝试其他方向或降低预期");
        stopLoss.setDeadline("6 个月后");
        stopLoss.setAlternativePath("结合自己的实际情况，动态调整目标");
        item.setStopLossAdvice(stopLoss);

        return item;
    }

    private PathResponse.PathItem buildSingleFallbackItem(String message) {
        PathResponse.PathItem item = new PathResponse.PathItem();
        item.setPathType(0);
        item.setPathName("📌 系统降级方案");
        item.setPathSummary(message);
        item.setDescription("AI 服务暂时无法响应，这是系统提供的通用建议。请稍后重新生成。");
        item.setTimeline(List.of(Map.of("year", "近期", "action", "稍后重新尝试生成规划")));
        item.setKeyNodes(List.of(Map.of("node", "等待 AI 服务恢复", "deadline", "30 分钟后重试")));
        item.setSkillGap(List.of("当前 AI 服务不可用"));
        item.setRiskFactors(List.of("AI 服务临时故障", "建议稍后重试"));
        item.setRecommendedActions(List.of("等待 30 分钟后重新生成", "检查网络连接", "联系管理员"));

        PathResponse.SalaryExpectation salary = new PathResponse.SalaryExpectation();
        salary.setEntry(0);
        salary.setMid(0);
        salary.setSenior(0);
        item.setSalaryExpectation(salary);

        return item;
    }

    @Override
    public String generatePlanAsyncWithBreaker(Long userId, ProfileRequest request) {
        String taskId = UUID.randomUUID().toString().replace("-", "");

        TaskResponse task = new TaskResponse();
        task.setTaskId(taskId);
        task.setStatus("PENDING");
        task.setCreatedAt(System.currentTimeMillis());

        String redisKey = TASK_PREFIX + taskId;
        try {
            // ✅ 统一存 JSON 字符串
            String taskJson = objectMapper.writeValueAsString(task);
            redisTemplate.opsForValue().set(redisKey, taskJson, Duration.ofMinutes(TASK_TTL_MINUTES));
        } catch (Exception e) {
            log.error("初始化任务状态失败", e);
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        executeAiTaskWithBreakerAsync(userId, request, taskId);
        return taskId;
    }

    @Async("taskExecutor")
    public void executeAiTaskWithBreakerAsync(Long userId, ProfileRequest request, String taskId) {
        String redisKey = TASK_PREFIX + taskId;
        try {
            log.info("开始异步 AI 生成（带熔断），taskId: {}", taskId);

            PathResponse result = generatePlanWithBreaker(userId, request);

            // ✅ 读取 JSON 字符串并反序列化
            String taskJson = (String) redisTemplate.opsForValue().get(redisKey);
            if (taskJson != null) {
                TaskResponse task = objectMapper.readValue(taskJson, TaskResponse.class);
                task.setStatus("SUCCESS");
                task.setResult(result);
                String newTaskJson = objectMapper.writeValueAsString(task);
                redisTemplate.opsForValue().set(redisKey, newTaskJson, Duration.ofMinutes(TASK_TTL_MINUTES));
            }
            log.info("AI 生成完成（带熔断），taskId: {}", taskId);

        } catch (Exception e) {
            log.error("AI 生成失败，taskId: {}", taskId, e);
            try {
                String taskJson = (String) redisTemplate.opsForValue().get(redisKey);
                if (taskJson != null) {
                    TaskResponse task = objectMapper.readValue(taskJson, TaskResponse.class);
                    task.setStatus("FAILED");
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                    task.setError("AI 服务暂时不可用: " + errorMsg);
                    String newTaskJson = objectMapper.writeValueAsString(task);
                    redisTemplate.opsForValue().set(redisKey, newTaskJson, Duration.ofMinutes(TASK_TTL_MINUTES));
                }
            } catch (Exception ex) {
                log.error("更新任务状态失败，taskId: {}", taskId, ex);
            }
        }
    }

    private PathResponse savePlanAndBuildResponse(Long userId, ProfileRequest request,
                                                  List<Map<String, Object>> paths) throws Exception {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setGoalType(request.getGoalType());
        profile.setMajor(request.getMajor());
        profile.setGpa(request.getGpa());
        profile.setSchoolLevel(request.getSchoolLevel());
        profile.setEnglishLevel(request.getEnglishLevel());
        profile.setIsPartyMember(request.getIsPartyMember() != null && request.getIsPartyMember());
        profile.setGraduationYear(request.getGraduationYear());
        profile.setGoalSpecificData(objectMapper.writeValueAsString(request.getGoalData()));
        profile.setPersonalityTags(objectMapper.writeValueAsString(request.getPersonalityTags()));
        profile.setIsActive(true);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.insert(profile);

        PlanRecord plan = new PlanRecord();
        plan.setUserId(userId);
        plan.setProfileId(profile.getProfileId());
        plan.setIsFavorite(false);
        plan.setIsDeleted(false);
        plan.setGoalType(request.getGoalType());
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planRecordMapper.insert(plan);

        List<PathResponse.PathItem> pathItems = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            Map<String, Object> p = paths.get(i);
            PathDetail detail = new PathDetail();
            detail.setPlanId(plan.getPlanId());
            detail.setPathType((Integer) p.getOrDefault("pathType", i + 1));
            detail.setPathName((String) p.get("pathName"));
            detail.setPathSummary((String) p.get("pathSummary"));
            detail.setDescription((String) p.get("description"));
            detail.setTimeline(objectMapper.writeValueAsString(p.get("timeline")));
            detail.setKeyNodes(objectMapper.writeValueAsString(p.get("keyNodes")));
            detail.setSkillGap(objectMapper.writeValueAsString(p.get("skillGap")));

            Object salaryObj = p.get("salaryExpectation");
            if (salaryObj == null) {
                salaryObj = calculateSmartSalary(request.getGoalType(), detail.getPathType(), request);
                log.info("AI 未返回 salaryExpectation，使用画像计算兜底: pathType={}, salary={}", detail.getPathType(), salaryObj);
            }
            detail.setSalaryExpectation(objectMapper.writeValueAsString(salaryObj));

            // ====== ✅ 新增：处理 stopLossAdvice ======
            Object stopLossObj = p.get("stopLossAdvice");
            if (stopLossObj != null) {
                detail.setStopLossAdvice(objectMapper.writeValueAsString(stopLossObj));
            }

            detail.setRiskFactors(objectMapper.writeValueAsString(p.get("riskFactors")));
            detail.setRecommendedActions(objectMapper.writeValueAsString(p.get("recommendedActions")));
            detail.setSortOrder(i + 1);
            detail.setCreatedAt(LocalDateTime.now());
            pathDetailMapper.insert(detail);

            PathResponse.PathItem item = new PathResponse.PathItem();
            item.setPathType(detail.getPathType());
            item.setPathName(detail.getPathName());
            item.setPathSummary(detail.getPathSummary());
            item.setDescription(detail.getDescription());
            item.setTimeline(objectMapper.readValue(detail.getTimeline(), new TypeReference<>() {}));
            item.setKeyNodes(objectMapper.readValue(detail.getKeyNodes(), new TypeReference<>() {}));
            item.setSkillGap(objectMapper.readValue(detail.getSkillGap(), new TypeReference<>() {}));
            item.setSalaryExpectation(objectMapper.readValue(detail.getSalaryExpectation(), new TypeReference<PathResponse.SalaryExpectation>() {}));

            // ====== ✅ 新增：读取 stopLossAdvice ======
            if (detail.getStopLossAdvice() != null && !detail.getStopLossAdvice().isEmpty()) {
                item.setStopLossAdvice(objectMapper.readValue(
                        detail.getStopLossAdvice(),
                        new TypeReference<PathResponse.StopLossAdvice>() {}
                ));
            }

            item.setRiskFactors(objectMapper.readValue(detail.getRiskFactors(), new TypeReference<>() {}));
            item.setRecommendedActions(objectMapper.readValue(detail.getRecommendedActions(), new TypeReference<>() {}));
            pathItems.add(item);
        }

        PathResponse response = new PathResponse();
        response.setPlanId(plan.getPlanId());
        response.setPaths(pathItems);
        return response;
    }

    @Override
    public PathResponse getPlanDetail(Long planId, Long userId) throws Exception {
        // ✅ 权限校验：确保该规划属于当前用户
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
            // ====== ✅ 新增：读取 stopLossAdvice ======
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
    @Transactional(rollbackFor = Exception.class)
    public void toggleFavorite(Long planId, Long userId) {
        PlanRecord plan = planRecordMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PLAN_NOT_EXIST);
        }
        plan.setIsFavorite(!plan.getIsFavorite());
        plan.setUpdatedAt(LocalDateTime.now());
        planRecordMapper.updateById(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long planId, Long userId) {
        PlanRecord plan = planRecordMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PLAN_NOT_EXIST);
        }
        planRecordMapper.deleteById(planId);
    }

    /**
     * 根据用户画像智能计算薪资预期（使用规则引擎）
     * - 就业: 单位为 K/月
     * - 考研: entry/mid/senior 表示预估排名百分位（越小越好）
     * - 考公: entry/mid/senior 表示预估职级
     */
    private Map<String, Integer> calculateSmartSalary(Integer goalType, Integer pathType, ProfileRequest request) {
        // ====== 考研方向 ======
        if (goalType == 2) {
            double gpaFactor = request.getGpa() != null ? Math.max(0.7, request.getGpa() / 4.0) : 0.85;
            double schoolFactor = switch (request.getSchoolLevel() != null ? request.getSchoolLevel() : 3) {
                case 1 -> 0.70;
                case 2 -> 0.85;
                default -> 1.0;
            };
            double score = gpaFactor * schoolFactor;
            return Map.of(
                    "entry", (int) Math.round(50 * score),
                    "mid", (int) Math.round(25 * score),
                    "senior", (int) Math.round(10 * score)
            );
        }

        // ====== 考公方向 ======
        if (goalType == 3) {
            return Map.of(
                    "entry", 1,
                    "mid", (int) (3 + (request.getIsPartyMember() != null && request.getIsPartyMember() ? 1 : 0)),
                    "senior", (int) (6 + (request.getSchoolLevel() != null && request.getSchoolLevel() <= 2 ? 2 : 0))
            );
        }

        // ====== 就业方向（使用规则引擎） ======

        // 1. 构建规则引擎上下文
        Map<String, Object> context = new HashMap<>();
        context.put("goalType", goalType);
        context.put("pathType", pathType);
        context.put("schoolLevel", request.getSchoolLevel() != null ? request.getSchoolLevel() : 3);
        context.put("gpa", request.getGpa() != null ? request.getGpa() : 3.0);
        context.put("englishLevel", request.getEnglishLevel() != null ? request.getEnglishLevel() : 4);
        context.put("internshipCount", request.getGoalData() != null
                ? request.getGoalData().getOrDefault("internshipCount", 0)
                : 0);

        // 2. 使用规则引擎计算综合系数
        double factor = salaryRuleEngine.evaluate("SALARY", context, 1.0);
        log.info("薪资规则引擎计算完成，综合系数: {}", factor);

        // 3. 获取基础薪资
        int baseEntry, baseMid, baseSenior;
        switch (pathType != null ? pathType : 1) {
            case 1 -> { baseEntry = 12; baseMid = 22; baseSenior = 40; }
            case 2 -> { baseEntry = 8;  baseMid = 16; baseSenior = 28; }
            case 3 -> { baseEntry = 6;  baseMid = 18; baseSenior = 38; }
            default -> { baseEntry = 10; baseMid = 20; baseSenior = 35; }
        }

        // 4. 应用城市系数（从 goalData 中提取）
        String preferredCity = request.getGoalData() != null
                ? (String) request.getGoalData().get("preferredCity")
                : null;
        double cityFactor = getCityFactor(preferredCity);
        double finalFactor = factor * cityFactor;

        // 5. 计算结果（四舍五入到偶数）
        return Map.of(
                "entry", Math.max(4, (int) Math.round(baseEntry * finalFactor / 2.0) * 2),
                "mid", Math.max(8, (int) Math.round(baseMid * finalFactor / 2.0) * 2),
                "senior", Math.max(15, (int) Math.round(baseSenior * finalFactor / 2.0) * 2)
        );
    }

    private double getCityFactor(String city) {
        if (city == null || city.isEmpty()) return 1.0;

        String c = city.toLowerCase();
        if (c.contains("北京") || c.contains("上海") || c.contains("深圳") || c.contains("广州") || c.contains("杭州")) {
            return 1.0;
        } else if (c.contains("南京") || c.contains("武汉") || c.contains("成都") || c.contains("西安")
                || c.contains("苏州") || c.contains("重庆") || c.contains("天津")) {
            return 0.85;
        } else if (c.contains("家乡") || c.contains("省会") || c.contains("二三线")) {
            return 0.70;
        } else {
            return 0.80;
        }
    }

    @Override
    public String generatePlanAsync(Long userId, ProfileRequest request) {
        String taskId = UUID.randomUUID().toString().replace("-", "");

        TaskResponse task = new TaskResponse();
        task.setTaskId(taskId);
        task.setStatus("PENDING");
        task.setCreatedAt(System.currentTimeMillis());

        String redisKey = TASK_PREFIX + taskId;
        try {
            redisTemplate.opsForValue().set(redisKey, task, Duration.ofMinutes(TASK_TTL_MINUTES));
        } catch (Exception e) {
            log.error("初始化任务状态失败", e);
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        executeAiTaskAsync(userId, request, taskId);
        return taskId;
    }

    @Async("taskExecutor")
    public void executeAiTaskAsync(Long userId, ProfileRequest request, String taskId) {
        String redisKey = TASK_PREFIX + taskId;
        try {
            log.info("开始异步 AI 生成，taskId: {}", taskId);

            PathResponse result = generatePlan(userId, request);

            Object cached = redisTemplate.opsForValue().get(redisKey);
            TaskResponse task;
            if (cached instanceof TaskResponse tr) {
                task = tr;
            } else if (cached instanceof String s) {
                task = objectMapper.readValue(s, TaskResponse.class);
            } else {
                log.warn("任务状态不存在或已过期，taskId: {}", taskId);
                return;
            }

            task.setStatus("SUCCESS");
            task.setResult(result);
            redisTemplate.opsForValue().set(redisKey, task, Duration.ofMinutes(TASK_TTL_MINUTES));
            log.info("AI 生成完成，taskId: {}", taskId);

        } catch (Exception e) {
            log.error("AI 生成失败，taskId: {}", taskId, e);
            try {
                Object cached = redisTemplate.opsForValue().get(redisKey);
                TaskResponse task;
                if (cached instanceof TaskResponse tr) {
                    task = tr;
                } else if (cached instanceof String s) {
                    task = objectMapper.readValue(s, TaskResponse.class);
                } else {
                    return;
                }
                task.setStatus("FAILED");
                task.setError(e.getMessage() != null ? e.getMessage() : "未知错误");
                redisTemplate.opsForValue().set(redisKey, task, Duration.ofMinutes(TASK_TTL_MINUTES));
            } catch (Exception ex) {
                log.error("更新任务状态失败，taskId: {}", taskId, ex);
            }
        }
    }

    @Override
    public TaskResponse getTaskStatus(String taskId) throws Exception {
        String redisKey = TASK_PREFIX + taskId;
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached == null) {
            throw new BusinessException(404, "任务不存在或已过期");
        }
        if (cached instanceof TaskResponse tr) {
            return tr;
        }
        if (cached instanceof String s) {
            return objectMapper.readValue(s, TaskResponse.class);
        }
        throw new BusinessException(500, "任务状态解析异常");
    }

    @Override
    public List<PlanRecord> getPlanRecordsByUserId(Long userId) {
        LambdaQueryWrapper<PlanRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlanRecord::getUserId, userId)
                .eq(PlanRecord::getIsDeleted, false)
                .orderByDesc(PlanRecord::getCreatedAt);
        List<PlanRecord> records = planRecordMapper.selectList(wrapper);
        // 填充 pathNames...
        return records;
    }

    @Override
    public IPage<PlanRecord> getPlanRecordsByUserId(Long userId, Integer page, Integer size) {
        Page<PlanRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PlanRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlanRecord::getUserId, userId)
                .eq(PlanRecord::getIsDeleted, false)
                .orderByDesc(PlanRecord::getCreatedAt);
        IPage<PlanRecord> recordPage = planRecordMapper.selectPage(pageParam, wrapper);

        // 填充 pathNames
        for (PlanRecord record : recordPage.getRecords()) {
            LambdaQueryWrapper<PathDetail> pathWrapper = new LambdaQueryWrapper<>();
            pathWrapper.eq(PathDetail::getPlanId, record.getPlanId())
                    .orderByAsc(PathDetail::getSortOrder)
                    .select(PathDetail::getPathName);
            List<PathDetail> details = pathDetailMapper.selectList(pathWrapper);
            record.setPathNames(details.stream()
                    .map(PathDetail::getPathName)
                    .collect(Collectors.toList()));
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
            if (goalType == null || goalType < 1 || goalType > 3) continue;
            PlanRecord existing = latestByGoal.get(goalType);
            if (existing == null || record.getCreatedAt().isAfter(existing.getCreatedAt())) {
                latestByGoal.put(goalType, record);
            }
        }

        // 3. 对每种目标获取路径详情（传入 userId 校验权限）
        for (int goalType = 1; goalType <= 3; goalType++) {
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