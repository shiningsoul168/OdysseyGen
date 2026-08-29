package com.odysseygen.service.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 规则条件匹配器：解析 condition_expression（JSON），判断上下文是否命中。
 * 供薪资规则引擎、兜底规则引擎复用，避免每个引擎重复实现条件判断逻辑。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConditionMatcher {

    private final ObjectMapper objectMapper;

    /** 判断条件表达式是否命中上下文 */
    public boolean matches(String conditionJson, Map<String, Object> context) {
        try {
            if (conditionJson == null || conditionJson.isEmpty()) {
                return true;
            }

            Map<String, Object> condition = objectMapper.readValue(conditionJson, new TypeReference<>() {});

            String field = (String) condition.get("field");
            String operator = (String) condition.get("operator");
            Object value = condition.get("value");
            Object value2 = condition.get("value2");

            Object fieldValue = context.get(field);
            if (fieldValue == null) {
                return false;
            }

            if (fieldValue instanceof String) {
                return evaluateStringCondition((String) fieldValue, operator, value, value2);
            } else if (fieldValue instanceof Number) {
                return evaluateNumericCondition(((Number) fieldValue).doubleValue(), operator, value, value2);
            } else if (fieldValue instanceof Boolean) {
                return evaluateBooleanCondition((Boolean) fieldValue, operator, value);
            } else {
                return evaluateObjectCondition(fieldValue, operator, value, value2);
            }
        } catch (Exception e) {
            log.warn("条件匹配失败: {}, 错误: {}", conditionJson, e.getMessage());
            return false;
        }
    }

    /** 判断规则是否适用于当前 goalType（applicable_goal_types 逗号分隔） */
    public boolean applicable(String applicableGoalTypes, Map<String, Object> context) {
        if (applicableGoalTypes == null || applicableGoalTypes.isEmpty()) {
            return true;
        }
        Object rawGoalType = context.get("goalType");
        if (rawGoalType == null) {
            return true;
        }
        String goalTypeStr = rawGoalType.toString();
        for (String type : applicableGoalTypes.split(",")) {
            if (goalTypeStr.equals(type.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateStringCondition(String fieldValue, String operator, Object value, Object value2) {
        String strValue = value != null ? value.toString() : null;
        return switch (operator) {
            case "==", "=" -> fieldValue.equals(strValue);
            case "!=" -> !fieldValue.equals(strValue);
            case "in" -> {
                if (value instanceof List) {
                    yield ((List<?>) value).contains(fieldValue);
                }
                yield false;
            }
            default -> {
                log.warn("字符串类型不支持操作符: {}", operator);
                yield false;
            }
        };
    }

    private boolean evaluateNumericCondition(double fieldValue, String operator, Object value, Object value2) {
        double numValue = toDouble(value);
        double numValue2 = toDouble(value2);
        return switch (operator) {
            case "==", "=" -> fieldValue == numValue;
            case "!=" -> fieldValue != numValue;
            case ">" -> fieldValue > numValue;
            case ">=" -> fieldValue >= numValue;
            case "<" -> fieldValue < numValue;
            case "<=" -> fieldValue <= numValue;
            case "between" -> fieldValue >= numValue && fieldValue <= numValue2;
            default -> {
                log.warn("未知操作符: {}", operator);
                yield false;
            }
        };
    }

    private boolean evaluateBooleanCondition(Boolean fieldValue, String operator, Object value) {
        boolean boolValue = value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(value.toString());
        return switch (operator) {
            case "==", "=" -> fieldValue == boolValue;
            case "!=" -> fieldValue != boolValue;
            default -> {
                log.warn("布尔类型不支持操作符: {}", operator);
                yield false;
            }
        };
    }

    private boolean evaluateObjectCondition(Object fieldValue, String operator, Object value, Object value2) {
        return evaluateStringCondition(fieldValue.toString(), operator, value, value2);
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
