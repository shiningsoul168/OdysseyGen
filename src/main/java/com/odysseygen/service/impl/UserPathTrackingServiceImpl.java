package com.odysseygen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.odysseygen.common.BusinessException;
import com.odysseygen.dto.request.TrackPathRequest;
import com.odysseygen.dto.response.UserPathTrackingResponse;
import com.odysseygen.entity.PlanRecord;
import com.odysseygen.entity.UserPathTracking;
import com.odysseygen.enums.PathTypeEnum;
import com.odysseygen.enums.TrackingStatusEnum;
import com.odysseygen.mapper.PlanRecordMapper;
import com.odysseygen.mapper.UserPathTrackingMapper;
import com.odysseygen.service.MilestoneTrackingService;
import com.odysseygen.service.UserPathTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPathTrackingServiceImpl implements UserPathTrackingService {

    private final UserPathTrackingMapper trackingMapper;
    private final PlanRecordMapper planRecordMapper;
    private final MilestoneTrackingService milestoneService;

    private static final int STATUS_IN_PROGRESS = TrackingStatusEnum.IN_PROGRESS.getCode();
    private static final int STATUS_COMPLETED = TrackingStatusEnum.COMPLETED.getCode();
    private static final int STATUS_ABANDONED = TrackingStatusEnum.ABANDONED.getCode();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public UserPathTrackingResponse selectPath(Long userId, Long planId, Integer pathType) {
        // 1. 检查规划是否存在
        PlanRecord plan = planRecordMapper.selectById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new BusinessException("规划不存在或无权限");
        }

        // 2. 检查是否有进行中的路径（如果有，先放弃）
        LambdaQueryWrapper<UserPathTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPathTracking::getUserId, userId)
                .eq(UserPathTracking::getStatus, STATUS_IN_PROGRESS);
        UserPathTracking existing = trackingMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setStatus(STATUS_ABANDONED);
            existing.setUpdatedAt(LocalDateTime.now());
            trackingMapper.updateById(existing);
            log.info("用户 {} 放弃之前的路径，trackingId: {}", userId, existing.getTrackingId());
        }

        // 3. 创建新的跟踪记录
        UserPathTracking tracking = new UserPathTracking();
        tracking.setUserId(userId);
        tracking.setPlanId(planId);
        tracking.setPathType(pathType);
        tracking.setStatus(STATUS_IN_PROGRESS);
        tracking.setStartedAt(LocalDateTime.now());
        tracking.setCreatedAt(LocalDateTime.now());
        tracking.setUpdatedAt(LocalDateTime.now());
        trackingMapper.insert(tracking);

        log.info("用户 {} 选定路径成功，trackingId: {}", userId, tracking.getTrackingId());

        milestoneService.initMilestones(userId, tracking.getTrackingId(), planId, pathType);
        return buildResponse(tracking);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPathTrackingResponse updateStatus(Long userId, Integer status) {
        // 1. 获取当前进行中的路径
        UserPathTracking tracking = getActiveTracking(userId);
        if (tracking == null) {
            throw new BusinessException("暂无进行中的路径");
        }

        // 2. 状态校验
        if (status.equals(STATUS_COMPLETED) && tracking.getStatus().equals(STATUS_IN_PROGRESS)) {
            tracking.setStatus(STATUS_COMPLETED);
            tracking.setCompletedAt(LocalDateTime.now());
        } else if (status.equals(STATUS_ABANDONED)) {
            tracking.setStatus(STATUS_ABANDONED);
        } else {
            throw new BusinessException("状态更新无效，当前状态: " + tracking.getStatus());
        }

        tracking.setUpdatedAt(LocalDateTime.now());
        trackingMapper.updateById(tracking);

        log.info("用户 {} 更新路径状态: {}", userId, status);
        return buildResponse(tracking);
    }

    @Override
    public UserPathTrackingResponse getCurrentTracking(Long userId) {
        UserPathTracking tracking = getActiveTracking(userId);
        if (tracking == null) {
            return null;
        }
        return buildResponse(tracking);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void abandonPath(Long userId) {
        UserPathTracking tracking = getActiveTracking(userId);
        if (tracking == null) {
            throw new BusinessException("暂无进行中的路径");
        }

        tracking.setStatus(STATUS_ABANDONED);
        tracking.setUpdatedAt(LocalDateTime.now());
        trackingMapper.updateById(tracking);
        log.info("用户 {} 放弃路径，trackingId: {}", userId, tracking.getTrackingId());
    }

    /**
     * 获取用户当前进行中的路径
     */
    private UserPathTracking getActiveTracking(Long userId) {
        LambdaQueryWrapper<UserPathTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPathTracking::getUserId, userId)
                .eq(UserPathTracking::getStatus, STATUS_IN_PROGRESS);
        return trackingMapper.selectOne(wrapper);
    }

    private UserPathTrackingResponse buildResponse(UserPathTracking tracking) {
        UserPathTrackingResponse response = new UserPathTrackingResponse();
        response.setTrackingId(tracking.getTrackingId());
        response.setPlanId(tracking.getPlanId());
        response.setPathType(tracking.getPathType());

        response.setPathTypeLabel(PathTypeEnum.labelOf(tracking.getPathType()));

        response.setStatus(tracking.getStatus());
        response.setStatusLabel(TrackingStatusEnum.labelOf(tracking.getStatus()));

        if (tracking.getStartedAt() != null) {
            response.setStartedAt(tracking.getStartedAt().format(FORMATTER));
        }
        if (tracking.getCompletedAt() != null) {
            response.setCompletedAt(tracking.getCompletedAt().format(FORMATTER));
        }
        response.setNotes(tracking.getNotes());
        return response;
    }
}
