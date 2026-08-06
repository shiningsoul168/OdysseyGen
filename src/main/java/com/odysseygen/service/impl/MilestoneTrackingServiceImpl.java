package com.odysseygen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.odysseygen.common.BusinessException;
import com.odysseygen.dto.request.UpdateMilestoneRequest;
import com.odysseygen.dto.response.MilestoneProgressResponse;
import com.odysseygen.dto.response.PathResponse;
import com.odysseygen.entity.UserMilestoneTracking;
import com.odysseygen.entity.UserPathTracking;
import com.odysseygen.mapper.UserMilestoneTrackingMapper;
import com.odysseygen.mapper.UserPathTrackingMapper;
import com.odysseygen.service.MilestoneTrackingService;
import com.odysseygen.service.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MilestoneTrackingServiceImpl implements MilestoneTrackingService {

    private final UserMilestoneTrackingMapper milestoneMapper;
    private final UserPathTrackingMapper trackingMapper;
    private final PlanService planService;

    private static final int STATUS_NOT_STARTED = 0;
    private static final int STATUS_COMPLETED = 2;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public void initMilestones(Long userId, Long trackingId, Long planId, Integer pathType) {
        try {
            // 1. 检查是否已有里程碑，如有则删除（保证幂等）
            LambdaQueryWrapper<UserMilestoneTracking> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(UserMilestoneTracking::getTrackingId, trackingId);
            List<UserMilestoneTracking> existing = milestoneMapper.selectList(checkWrapper);
            if (!existing.isEmpty()) {
                for (UserMilestoneTracking m : existing) {
                    milestoneMapper.deleteById(m.getId());
                }
                log.info("删除已存在的里程碑，trackingId: {}", trackingId);
            }

            // 2. 获取该规划的三条路径
            PathResponse response = planService.getPlanDetail(planId, userId);
            List<PathResponse.PathItem> paths = response.getPaths();

            // 3. 找到对应的路径（根据 pathType）
            PathResponse.PathItem targetPath = paths.stream()
                    .filter(p -> p.getPathType().equals(pathType))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("未找到对应的路径"));

            // 4. 获取 keyNodes
            List<Map<String, String>> keyNodes = targetPath.getKeyNodes();
            if (keyNodes == null || keyNodes.isEmpty()) {
                log.warn("该路径没有里程碑数据，pathType: {}", pathType);
                return;
            }

            // 5. 批量插入里程碑记录
            List<UserMilestoneTracking> milestones = new ArrayList<>();
            for (int i = 0; i < keyNodes.size(); i++) {
                Map<String, String> node = keyNodes.get(i);
                UserMilestoneTracking milestone = new UserMilestoneTracking();
                milestone.setUserId(userId);
                milestone.setTrackingId(trackingId);
                milestone.setPlanId(planId);
                milestone.setPathType(pathType);
                milestone.setNodeIndex(i);
                milestone.setNodeName(node.get("node"));
                milestone.setNodeDeadline(node.get("deadline"));
                milestone.setStatus(STATUS_NOT_STARTED);
                milestone.setCreatedAt(LocalDateTime.now());
                milestone.setUpdatedAt(LocalDateTime.now());
                milestones.add(milestone);
            }

            for (UserMilestoneTracking m : milestones) {
                milestoneMapper.insert(m);
            }

            log.info("初始化里程碑成功，用户: {}, 路径: {}, 里程碑数: {}", userId, pathType, milestones.size());

        } catch (Exception e) {
            log.error("初始化里程碑失败", e);
            String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
            throw new BusinessException("初始化里程碑失败: " + msg);
        }
    }

    @Override
    @Transactional
    public MilestoneProgressResponse updateMilestoneStatus(Long userId, UpdateMilestoneRequest request) {
        // 1. 查询里程碑
        UserMilestoneTracking milestone = milestoneMapper.selectById(request.getMilestoneId());
        if (milestone == null) {
            throw new BusinessException("里程碑不存在");
        }
        if (!milestone.getUserId().equals(userId)) {
            throw new BusinessException("无权限操作");
        }

        // 2. 检查状态流转合法性
        Integer newStatus = request.getStatus();
        if (newStatus < 0 || newStatus > 2) {
            throw new BusinessException("状态值无效");
        }

        // 3. 更新状态
        milestone.setStatus(newStatus);
        if (newStatus == STATUS_COMPLETED) {
            milestone.setCompletedAt(LocalDateTime.now());
        } else {
            milestone.setCompletedAt(null);
        }
        milestone.setUpdatedAt(LocalDateTime.now());
        milestoneMapper.updateById(milestone);

        log.info("里程碑状态更新成功，id: {}, status: {}", request.getMilestoneId(), newStatus);

        // 4. 获取最新进度
        MilestoneProgressResponse progress = getMilestoneProgress(userId);

        // 5. 检查是否全部完成，更新跟踪表
        if (progress != null &&
                progress.getCompletedMilestones() != null &&
                progress.getTotalMilestones() != null &&
                progress.getCompletedMilestones().equals(progress.getTotalMilestones()) &&
                progress.getTotalMilestones() > 0) {

            LambdaQueryWrapper<UserPathTracking> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserPathTracking::getUserId, userId)
                    .eq(UserPathTracking::getStatus, 1);  // 进行中
            UserPathTracking tracking = trackingMapper.selectOne(wrapper);
            if (tracking != null) {
                tracking.setStatus(2);  // 已完成
                tracking.setCompletedAt(LocalDateTime.now());
                tracking.setUpdatedAt(LocalDateTime.now());
                trackingMapper.updateById(tracking);
                log.info("🎉 用户 {} 完成所有里程碑，路径跟踪已完结", userId);
            }
        }

        return progress;
    }

    @Override
    public MilestoneProgressResponse getMilestoneProgress(Long userId) {
        // 1. 获取当前进行中的路径跟踪
        LambdaQueryWrapper<UserPathTracking> trackingWrapper = new LambdaQueryWrapper<>();
        trackingWrapper.eq(UserPathTracking::getUserId, userId)
                .eq(UserPathTracking::getStatus, 1);
        UserPathTracking tracking = trackingMapper.selectOne(trackingWrapper);

        // 2. 如果没有进行中的，查最近完成的路径
        if (tracking == null) {
            LambdaQueryWrapper<UserPathTracking> completedWrapper = new LambdaQueryWrapper<>();
            completedWrapper.eq(UserPathTracking::getUserId, userId)
                    .eq(UserPathTracking::getStatus, 2)
                    .orderByDesc(UserPathTracking::getCompletedAt)
                    .last("LIMIT 1");
            tracking = trackingMapper.selectOne(completedWrapper);
        }

        if (tracking == null) {
            return null;
        }

        final UserPathTracking finalTracking = tracking;

        // 3. 查询该跟踪下的所有里程碑
        LambdaQueryWrapper<UserMilestoneTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMilestoneTracking::getUserId, userId)
                .eq(UserMilestoneTracking::getTrackingId, finalTracking.getTrackingId())
                .orderByAsc(UserMilestoneTracking::getNodeIndex);
        List<UserMilestoneTracking> milestones = milestoneMapper.selectList(wrapper);

        // 4. 计算进度
        int total = milestones.size();
        long completedCount = milestones.stream()
                .filter(m -> m.getStatus() == STATUS_COMPLETED)
                .count();
        int progressPercent = total > 0 ? (int) (completedCount * 100 / total) : 0;

        // 5. 获取路径名称
        String pathName = "";
        try {
            // ✅ 修复：传入 userId
            PathResponse detail = planService.getPlanDetail(finalTracking.getPlanId(), userId);
            pathName = detail.getPaths().stream()
                    .filter(p -> p.getPathType().equals(finalTracking.getPathType()))
                    .map(PathResponse.PathItem::getPathName)
                    .findFirst()
                    .orElse("");
        } catch (Exception e) {
            log.warn("获取路径名称失败", e);
        }

        // 6. 组装响应
        MilestoneProgressResponse response = new MilestoneProgressResponse();
        response.setTrackingId(finalTracking.getTrackingId());
        response.setPlanId(finalTracking.getPlanId());
        response.setPathType(finalTracking.getPathType());
        response.setPathName(pathName);
        response.setTotalMilestones(total);
        response.setCompletedMilestones((int) completedCount);
        response.setProgressPercent(progressPercent);

        if (finalTracking.getStartedAt() != null) {
            response.setStartedAt(finalTracking.getStartedAt().format(FORMATTER));
        }
        if (finalTracking.getCompletedAt() != null) {
            response.setCompletedAt(finalTracking.getCompletedAt().format(FORMATTER));
        }

        List<MilestoneProgressResponse.MilestoneItem> items = new ArrayList<>();
        for (UserMilestoneTracking m : milestones) {
            MilestoneProgressResponse.MilestoneItem item = new MilestoneProgressResponse.MilestoneItem();
            item.setId(m.getId());
            item.setNodeIndex(m.getNodeIndex());
            item.setNodeName(m.getNodeName());
            item.setNodeDeadline(m.getNodeDeadline());
            item.setStatus(m.getStatus());
            if (m.getCompletedAt() != null) {
                item.setCompletedAt(m.getCompletedAt().format(FORMATTER));
            }
            items.add(item);
        }
        response.setMilestones(items);

        return response;
    }
}