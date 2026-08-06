package com.odysseygen.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RuleConfigRequest {

    @NotBlank(message = "规则Key不能为空")
    private String ruleKey;

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @NotNull(message = "规则类型不能为空")
    private Integer ruleType;  // 1-硬性过滤 2-软性扣分 3-推荐加权

    private String applicableGoalTypes;  // 1,2,3

    @NotBlank(message = "条件表达式不能为空")
    private String conditionExpression;  // JSON

    @NotBlank(message = "动作表达式不能为空")
    private String actionExpression;     // JSON

    private Integer priority;
    private Boolean enabled;
    private String description;
}
