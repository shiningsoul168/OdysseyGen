package com.odysseygen.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RuleConfigResponse {
    private Integer ruleId;
    private String ruleKey;
    private String ruleName;
    private Integer ruleType;
    private String applicableGoalTypes;
    private String conditionExpression;
    private String actionExpression;
    private Integer priority;
    private Boolean enabled;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
