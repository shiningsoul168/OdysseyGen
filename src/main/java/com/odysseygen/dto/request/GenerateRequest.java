package com.odysseygen.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateRequest {
    @Valid
    @NotNull(message = "画像数据不能为空")
    private ProfileRequest profile;
}
