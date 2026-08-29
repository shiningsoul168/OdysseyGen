package com.odysseygen.service.rule.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseygen.entity.RuleConfig;
import com.odysseygen.mapper.RuleConfigMapper;
import com.odysseygen.service.rule.ConditionMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryRuleEngineTest {

    @Mock private RuleConfigMapper ruleConfigMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SalaryRuleEngine salaryRuleEngine;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // Redis 未命中
        ConditionMatcher conditionMatcher = new ConditionMatcher(objectMapper);
        salaryRuleEngine = new SalaryRuleEngine(ruleConfigMapper, redisTemplate, objectMapper, conditionMatcher);
    }

    @Test
    void testMatchingRuleAppliesMultiplier() {
        when(ruleConfigMapper.selectList(any())).thenReturn(List.of(buildSalaryRule(true)));

        Map<String, Object> context = Map.of("goalType", 1, "schoolLevel", 1);

        double result = salaryRuleEngine.evaluate(context, 1.0);

        // base 1.0 + (multiplier 1.15 - 1.0) = 1.15
        assertEquals(1.15, result, 0.0001);
    }

    @Test
    void testDisabledRuleIsSkipped() {
        when(ruleConfigMapper.selectList(any())).thenReturn(List.of(buildSalaryRule(false)));

        double result = salaryRuleEngine.evaluate(Map.of("goalType", 1, "schoolLevel", 1), 1.0);

        assertEquals(1.0, result, 0.0001);
    }

    @Test
    void testNoRulesReturnsDefault() {
        when(ruleConfigMapper.selectList(any())).thenReturn(Collections.emptyList());

        double result = salaryRuleEngine.evaluate(Map.of(), 1.0);

        assertEquals(1.0, result, 0.0001);
    }

    @Test
    void testSecondCallUsesLocalVolatileCache() {
        when(ruleConfigMapper.selectList(any())).thenReturn(List.of(buildSalaryRule(true)));

        salaryRuleEngine.evaluate(Map.of("goalType", 1, "schoolLevel", 1), 1.0);
        salaryRuleEngine.evaluate(Map.of("goalType", 1, "schoolLevel", 1), 1.0);

        // 第二次命中 volatile 本地缓存，不再查库
        verify(ruleConfigMapper, times(1)).selectList(any());
    }

    @Test
    void testRefreshCacheReloadsFromDb() {
        when(ruleConfigMapper.selectList(any())).thenReturn(List.of(buildSalaryRule(true)));

        salaryRuleEngine.evaluate(Map.of("goalType", 1, "schoolLevel", 1), 1.0);
        salaryRuleEngine.refreshCache();
        salaryRuleEngine.evaluate(Map.of("goalType", 1, "schoolLevel", 1), 1.0);

        verify(ruleConfigMapper, times(2)).selectList(any());
    }

    private RuleConfig buildSalaryRule(boolean enabled) {
        RuleConfig rule = new RuleConfig();
        rule.setRuleKey("salary_school_985");
        rule.setRuleName("985/211 院校加成");
        rule.setRuleType(3); // SALARY
        rule.setApplicableGoalTypes("1");
        rule.setConditionExpression("{\"field\":\"schoolLevel\",\"operator\":\"==\",\"value\":1}");
        rule.setActionExpression("{\"multiplier\":1.15}");
        rule.setPriority(100);
        rule.setEnabled(enabled);
        return rule;
    }
}
