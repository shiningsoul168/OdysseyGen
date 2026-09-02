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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 兜底规则引擎测试：验证 findTemplate 按 priority 降序返回第一条命中的模板
 * （回归测试：曾因加载后未排序导致高优先级规则可能被跳过）。
 */
@ExtendWith(MockitoExtension.class)
class FallbackRuleEngineTest {

    @Mock private RuleConfigMapper ruleConfigMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private FallbackRuleEngine engine;

    private RuleConfig rule(String ruleKey, int priority, Integer goalType, Integer pathType, boolean enabled) {
        RuleConfig r = new RuleConfig();
        r.setRuleKey(ruleKey);
        r.setRuleName(ruleKey);
        r.setRuleType(4);
        r.setPriority(priority);
        r.setEnabled(enabled);
        r.setApplicableGoalTypes(String.valueOf(goalType));
        r.setConditionExpression("{\"field\":\"pathType\",\"operator\":\"==\",\"value\":" + pathType + "}");
        return r;
    }

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        engine = new FallbackRuleEngine(
                ruleConfigMapper, redisTemplate, new ObjectMapper(), new ConditionMatcher(new ObjectMapper()));
    }

    @Test
    void testFindTemplateReturnsHighestPriorityWhenRulesUnordered() {
        // 故意乱序返回：高优先级(100)排最后，验证引擎会先按 priority 降序排序
        List<RuleConfig> rules = Arrays.asList(
                rule("low", 10, 1, 1, true),
                rule("mid", 50, 1, 1, true),
                rule("high", 100, 1, 1, true)
        );
        when(valueOperations.get(any())).thenReturn(null);
        when(ruleConfigMapper.selectList(any())).thenReturn(rules);

        RuleConfig result = engine.findTemplate(1, 1);

        assertNotNull(result);
        assertEquals("high", result.getRuleKey());
    }

    @Test
    void testDisabledRuleIsSkipped() {
        List<RuleConfig> rules = Arrays.asList(
                rule("disabledHigh", 100, 1, 1, false),
                rule("enabledLow", 10, 1, 1, true)
        );
        when(valueOperations.get(any())).thenReturn(null);
        when(ruleConfigMapper.selectList(any())).thenReturn(rules);

        RuleConfig result = engine.findTemplate(1, 1);

        assertNotNull(result);
        assertEquals("enabledLow", result.getRuleKey());
    }

    @Test
    void testGoalTypeNotApplicableSkipped() {
        List<RuleConfig> rules = Arrays.asList(
                rule("forGoal2", 100, 2, 1, true),
                rule("forGoal1", 10, 1, 1, true)
        );
        when(valueOperations.get(any())).thenReturn(null);
        when(ruleConfigMapper.selectList(any())).thenReturn(rules);

        RuleConfig result = engine.findTemplate(1, 1);

        assertNotNull(result);
        assertEquals("forGoal1", result.getRuleKey());
    }

    @Test
    void testPathTypeNotMatchedReturnsNull() {
        List<RuleConfig> rules = Arrays.asList(
                rule("path2", 100, 1, 2, true)
        );
        when(valueOperations.get(any())).thenReturn(null);
        when(ruleConfigMapper.selectList(any())).thenReturn(rules);

        assertNull(engine.findTemplate(1, 1));
    }

    @Test
    void testRefreshCacheReloadsRules() {
        when(valueOperations.get(any())).thenReturn(null);
        when(ruleConfigMapper.selectList(any()))
                .thenReturn(List.of(rule("first", 10, 1, 1, true)));

        assertEquals("first", engine.findTemplate(1, 1).getRuleKey());

        // 改规则后刷新缓存 → 下次加载应重新查库
        engine.refreshCache();
        when(ruleConfigMapper.selectList(any()))
                .thenReturn(List.of(rule("second", 20, 1, 1, true)));

        assertEquals("second", engine.findTemplate(1, 1).getRuleKey());
        verify(ruleConfigMapper, times(2)).selectList(any());
    }
}
