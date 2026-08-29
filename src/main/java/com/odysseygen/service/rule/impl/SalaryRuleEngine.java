package com.odysseygen.service.rule.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseygen.entity.RuleConfig;
import com.odysseygen.enums.RuleTypeEnum;
import com.odysseygen.mapper.RuleConfigMapper;
import com.odysseygen.service.rule.ConditionMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 薪资规则引擎：根据用户画像计算薪资加权系数（兜底用，AI 未返回薪资时生效）。
 * 只处理 SALARY 类型规则（rule_type=3），查询时就按类型过滤，避免全表扫描后内存过滤。
 * 缓存：本地 volatile 一级缓存 + Redis 二级缓存，双重检查锁保证并发下只加载一次。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryRuleEngine {

    private final RuleConfigMapper ruleConfigMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConditionMatcher conditionMatcher;

    private static final String CACHE_KEY = "rule:salary:all";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final double MIN_FACTOR = 0.5;
    private static final double MAX_FACTOR = 2.0;

    /** 本地一级缓存：只缓存 SALARY 类型的规则列表 */
    private volatile List<RuleConfig> cachedRules;

    public double evaluate(Map<String, Object> context) {
        return evaluate(context, 1.0);
    }

    public double evaluate(Map<String, Object> context, double defaultValue) {
        List<RuleConfig> rules = loadRules();

        double base = defaultValue;
        double totalBonus = 0.0;
        int matchedCount = 0;

        for (RuleConfig rule : rules) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) continue;
            if (!conditionMatcher.applicable(rule.getApplicableGoalTypes(), context)) continue;
            if (!conditionMatcher.matches(rule.getConditionExpression(), context)) continue;

            double actionResult = applyAction(rule);
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

    /** 失效本地 + Redis 两级缓存，下次 evaluate 会重新加载 */
    public void refreshCache() {
        synchronized (this) {
            cachedRules = null;
            redisTemplate.delete(CACHE_KEY);
        }
        log.info("规则缓存已刷新（本地 + Redis）");
    }

    /** 加载规则：volatile 读 -> 未命中则加锁 -> 双重检查 -> Redis -> DB */
    private List<RuleConfig> loadRules() {
        List<RuleConfig> rules = cachedRules;
        if (rules != null) {
            return rules;
        }
        synchronized (this) {
            rules = cachedRules;
            if (rules == null) {
                rules = loadFromRedisOrDb();
                cachedRules = rules;
            }
            return rules;
        }
    }

    private List<RuleConfig> loadFromRedisOrDb() {
        // 1. 从 Redis 获取
        String cachedJson = (String) redisTemplate.opsForValue().get(CACHE_KEY);
        if (cachedJson != null && !cachedJson.isEmpty()) {
            try {
                List<RuleConfig> rules = objectMapper.readValue(cachedJson, new TypeReference<List<RuleConfig>>() {});
                log.info("从 Redis 加载规则成功，共 {} 条", rules.size());
                return rules;
            } catch (Exception e) {
                log.warn("Redis 缓存反序列化失败，将从数据库重新加载", e);
            }
        }

        // 2. 从数据库加载：只查 SALARY 类型，避免全表扫描后再内存过滤
        LambdaQueryWrapper<RuleConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleConfig::getRuleType, RuleTypeEnum.SALARY.getCode());
        List<RuleConfig> rules = ruleConfigMapper.selectList(wrapper);
        if (rules == null || rules.isEmpty()) {
            rules = Collections.emptyList();
        } else {
            // 拷贝一份再排序，避免对 mapper 返回的（可能是不可变的）列表原地排序
            rules = new ArrayList<>(rules);
            rules.sort((a, b) -> Integer.compare(
                    b.getPriority() != null ? b.getPriority() : 0,
                    a.getPriority() != null ? a.getPriority() : 0));
            try {
                String json = objectMapper.writeValueAsString(rules);
                redisTemplate.opsForValue().set(CACHE_KEY, json, CACHE_TTL);
                log.info("规则写入 Redis 缓存成功，共 {} 条", rules.size());
            } catch (Exception e) {
                log.warn("规则写入 Redis 失败，不影响主流程", e);
            }
        }
        return rules;
    }

    private double applyAction(RuleConfig rule) {
        try {
            String actionJson = rule.getActionExpression();
            if (actionJson == null || actionJson.isEmpty()) {
                return 1.0;
            }

            Map<String, Object> action = objectMapper.readValue(actionJson, new TypeReference<>() {});

            // 只支持 multiplier，废弃 add
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
