package com.odysseygen.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrackPathRequest {

    @NotNull(message = "规划ID不能为空")
    private Long planId;

    @NotNull(message = "路径类型不能为空")
    private Integer pathType;  // 1-主流 2-备用 3-理想
}
