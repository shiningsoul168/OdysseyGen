package com.odysseygen.service.rule.impl;

import com.odysseygen.entity.RuleConfig;
import com.odysseygen.mapper.RuleConfigMapper;
import com.odysseygen.service.rule.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryRuleEngine implements RuleEngine {

    private final RuleConfigMapper ruleConfigMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY = "rule:salary:all";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final double MIN_FACTOR = 0.5;
    private static final double MAX_FACTOR = 2.0;

    private final Map<String, List<RuleConfig>> ruleCache = new ConcurrentHashMap<>();

    @Override
    public double evaluate(String ruleType, Map<String, Object> context) {
        return evaluate(ruleType, context, 1.0);
    }

    @Override
    public double evaluate(String ruleType, Map<String, Object> context, double defaultValue) {
        List<RuleConfig> rules = loadRules();

        double base = defaultValue;
        double totalBonus = 0.0;
        int matchedCount = 0;

        for (RuleConfig rule : rules) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) continue;
            if (!ruleType.equalsIgnoreCase(getRuleTypeName(rule.getRuleType()))) continue;
            if (!isApplicable(rule, context)) continue;
            if (!matchesCondition(rule, context)) continue;

            double actionResult = applyAction(rule, context);
            double bonus = actionResult - 1.0;
            totalBonus += bonus;
            matchedCount++;
            log.debug("规则命中: {} -> 加成: {}%", rule.getRuleName(), bonus * 100);
        }

        double result = base + totalBonus;
        result = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, result));

        if (matchedCount > 0) {
            log.debug("规则引擎执行完成，匹配 {} 条规则，总加成: {}%，最终系数: {}",
                    matchedCount, totalBonus * 100, result);
        }

        return result;
    }

    @Override
    public void refreshCache() {
        redisTemplate.delete(CACHE_KEY);
        ruleCache.remove(CACHE_KEY);
        log.info("规则缓存已刷新（Redis + 本地缓存）");
    }

    /**
     * 加载规则（JSON 字符串存储，避免序列化问题）
     */
    private List<RuleConfig> loadRules() {
        return ruleCache.computeIfAbsent(CACHE_KEY, key -> {
            // 1. 从 Redis 获取
            String cachedJson = (String) redisTemplate.opsForValue().get(key);
            if (cachedJson != null && !cachedJson.isEmpty()) {
                try {
                    List<RuleConfig> rules = objectMapper.readValue(
                            cachedJson,
                            new TypeReference<List<RuleConfig>>() {}
                    );
                    log.info("从 Redis 加载规则成功，共 {} 条", rules.size());
                    return rules;
                } catch (Exception e) {
                    log.warn("Redis 缓存反序列化失败，将从数据库重新加载", e);
                }
            }

            // 2. 从数据库加载
            List<RuleConfig> rules = ruleConfigMapper.selectList(null);
            if (rules == null || rules.isEmpty()) {
                rules = Collections.emptyList();
            } else {
                rules.sort((a, b) -> Integer.compare(
                        b.getPriority() != null ? b.getPriority() : 0,
                        a.getPriority() != null ? a.getPriority() : 0
                ));
                try {
                    String json = objectMapper.writeValueAsString(rules);
                    redisTemplate.opsForValue().set(key, json, CACHE_TTL);
                    log.info("规则写入 Redis 缓存成功，共 {} 条", rules.size());
                } catch (Exception e) {
                    log.warn("规则写入 Redis 失败，不影响主流程", e);
                }
            }
            return rules;
        });
    }

    private boolean isApplicable(RuleConfig rule, Map<String, Object> context) {
        String applicable = rule.getApplicableGoalTypes();
        if (applicable == null || applicable.isEmpty()) {
            return true;
        }

        Object rawGoalType = context.get("goalType");
        if (rawGoalType == null) {
            return true;
        }
        String goalTypeStr = rawGoalType.toString();

        String[] types = applicable.split(",");
        for (String type : types) {
            if (goalTypeStr.equals(type.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCondition(RuleConfig rule, Map<String, Object> context) {
        try {
            String conditionJson = rule.getConditionExpression();
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
            log.warn("条件匹配失败，规则: {}, 错误: {}", rule.getRuleKey(), e.getMessage());
            return false;
        }
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

    private double applyAction(RuleConfig rule, Map<String, Object> context) {
        try {
            String actionJson = rule.getActionExpression();
            if (actionJson == null || actionJson.isEmpty()) {
                return 1.0;
            }

            Map<String, Object> action = objectMapper.readValue(actionJson, new TypeReference<>() {});

            // ✅ 只支持 multiplier，废弃 add
            if (action.containsKey("multiplier")) {
                return toDouble(action.get("multiplier"));
            }

            // 兼容旧数据：如果只有 add，自动转换
            if (action.containsKey("add")) {
                double addValue = toDouble(action.get("add"));
                log.warn("规则 {} 使用了废弃的 add 字段，建议迁移到 multiplier", rule.getRuleKey());
                return 1.0 + addValue;
            }

            return 1.0;
        } catch (Exception e) {
            log.warn("动作执行失败，规则: {}, 错误: {}", rule.getRuleKey(), e.getMessage());
            return 1.0;
        }
    }

    private String getRuleTypeName(Integer ruleType) {
        if (ruleType == null) return "UNKNOWN";
        return switch (ruleType) {
            case 1 -> "FILTER";
            case 2 -> "RECOMMEND";
            case 3 -> "SALARY";
            default -> "UNKNOWN";
        };
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