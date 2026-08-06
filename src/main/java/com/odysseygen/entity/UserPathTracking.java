package com.odysseygen.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_path_tracking")
public class UserPathTracking {

    @TableId(type = IdType.AUTO)
    private Long trackingId;

    private Long userId;

    private Long planId;

    private Integer pathType;  // 1-主流 2-备用 3-理想

    private Integer status;    // 1-进行中 2-已完成 3-已放弃

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}