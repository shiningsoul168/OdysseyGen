package com.odysseygen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_config")
public class RuleConfig {

    @TableId(type = IdType.AUTO)
    private Integer ruleId;

    private String ruleKey;

    private String ruleName;

    /**
     * 规则类型：1-硬性过滤 2-软性扣分 3-薪资加权(SALARY)，见 RuleTypeEnum
     */
    private Integer ruleType;

    /**
     * 适用目标类型：1,2,3（逗号分隔）
     */
    private String applicableGoalTypes;

    /**
     * 条件表达式（JSON）
     */
    private String conditionExpression;

    /**
     * 动作表达式（JSON）
     */
    private String actionExpression;

    private Integer priority;

    private Boolean enabled;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
