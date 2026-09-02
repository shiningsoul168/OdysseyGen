package com.odysseygen.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ProfileRequest {
    @NotNull(message = "目标类型不能为空")
    private Integer goalType;

    @NotBlank(message = "专业名称不能为空")
    private String major;

    private Double gpa;
    private Integer schoolLevel;
    private Integer englishLevel;
    private Boolean isPartyMember;
    @NotNull(message = "毕业年份不能为空")
    private Integer graduationYear;

    private Map<String, Object> goalData;   // 动态字段（就业/考研/考公专属）
    private String[] personalityTags;
}
