package com.odysseygen.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_milestone_tracking")
public class UserMilestoneTracking {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long trackingId;

    private Long planId;

    private Integer pathType;

    private Integer nodeIndex;

    private String nodeName;

    private String nodeDeadline;

    private Integer status;  // 0-未开始 1-进行中 2-已完成

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}