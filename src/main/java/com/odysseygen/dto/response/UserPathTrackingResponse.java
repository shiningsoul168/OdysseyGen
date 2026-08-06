package com.odysseygen.dto.response;

import lombok.Data;

@Data
public class UserPathTrackingResponse {
    private Long trackingId;
    private Long planId;
    private Integer pathType;
    private String pathTypeLabel;
    private String pathName;        // 路径名称
    private Integer status;
    private String statusLabel;
    private String startedAt;
    private String completedAt;
    private String notes;
}
