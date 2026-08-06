package com.odysseygen.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMilestoneRequest {

    @NotNull(message = "里程碑ID不能为空")
    private Long milestoneId;

    @NotNull(message = "状态不能为空")
    private Integer status;  // 0-未开始 1-进行中 2-已完成
}
