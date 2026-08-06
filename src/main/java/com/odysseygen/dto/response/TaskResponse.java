package com.odysseygen.dto.response;

import lombok.Data;

@Data
public class TaskResponse {
    private String taskId;
    private String status;  // PENDING / SUCCESS / FAILED
    private PathResponse result;
    private String error;
    private Long createdAt;
}
