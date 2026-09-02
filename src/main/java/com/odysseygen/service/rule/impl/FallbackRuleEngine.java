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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 兜底规则引擎：根据画像（goalType + pathType）匹配兜底模板。
 * 只处理 FALLBACK 类型规则（rule_type=4），缓存策略与 SalaryRuleEngine 一致。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FallbackRuleEngine {

    private final RuleConfigMapper ruleConfigMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConditionMatcher conditionMatcher;

    private static final String CACHE_KEY = "rule:fallback:all";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /** 本地一级缓存：只缓存 FALLBACK 类型的规则列表 */
    private volatile List<RuleConfig> cachedRules;

    public RuleConfig findTemplate(Integer goalType, Integer pathType) {
        List<RuleConfig> rules = loadRules();
        Map<String, Object> context = new HashMap<>();
        context.put("goalType", goalType);
        context.put("pathType", pathType);

        for (RuleConfig rule : rules) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) continue;
            if (!conditionMatcher.applicable(rule.getApplicableGoalTypes(), context)) continue;
            if (!conditionMatcher.matches(rule.getConditionExpression(), context)) continue;
            return rule;
        }
        return null;
    }

    /** 失效本地 + Redis 两级缓存 */
    public void refreshCache() {
        synchronized (this) {
            cachedRules = null;
            redisTemplate.delete(CACHE_KEY);
        }
        log.info("兜底规则缓存已刷新（本地 + Redis）");
    }

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
        String cachedJson = (String) redisTemplate.opsForValue().get(CACHE_KEY);
        if (cachedJson != null && !cachedJson.isEmpty()) {
            try {
                List<RuleConfig> rules = objectMapper.readValue(cachedJson, new TypeReference<List<RuleConfig>>() {});
                log.info("从 Redis 加载兜底模板成功，共 {} 条", rules.size());
                return rules;
            } catch (Exception e) {
                log.warn("Redis 缓存反序列化失败，将从数据库重新加载", e);
            }
        }

        LambdaQueryWrapper<RuleConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleConfig::getRuleType, RuleTypeEnum.FALLBACK.getCode());
        List<RuleConfig> rules = ruleConfigMapper.selectList(wrapper);
        if (rules == null || rules.isEmpty()) {
            rules = Collections.emptyList();
        } else {
            rules = new ArrayList<>(rules);
            // 按优先级降序排序：findTemplate 返回第一条命中的模板，必须保证高优先级规则优先被匹配
            // （与 SalaryRuleEngine 的加载逻辑保持一致，否则规则命中顺序不稳定）
            rules.sort((a, b) -> Integer.compare(
                    b.getPriority() != null ? b.getPriority() : 0,
                    a.getPriority() != null ? a.getPriority() : 0));
            try {
                String json = objectMapper.writeValueAsString(rules);
                redisTemplate.opsForValue().set(CACHE_KEY, json, CACHE_TTL);
                log.info("兜底模板写入 Redis 缓存成功，共 {} 条", rules.size());
            } catch (Exception e) {
                log.warn("规则写入 Redis 失败，不影响主流程", e);
            }
        }
        return rules;
    }
}
