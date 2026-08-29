package com.odysseygen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.entity.RuleConfig;
import com.odysseygen.service.rule.impl.FallbackRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FallbackServiceTest {

    @Mock private FallbackRuleEngine fallbackRuleEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private FallbackService fallbackService;

    @BeforeEach
    void setUp() {
        fallbackService = new FallbackService(fallbackRuleEngine, objectMapper);
    }

    @Test
    void testGeneratePathsProducesThreePathsAndFillsPlaceholder() throws Exception {
        ProfileRequest request = new ProfileRequest();
        request.setGoalType(1);
        request.setMajor("软件工程");

        when(fallbackRuleEngine.findTemplate(any(), any())).thenReturn(buildTemplate());

        List<Map<String, Object>> paths = fallbackService.generatePaths(request);

        assertEquals(3, paths.size());
        // 占位符 {major} 被替换
        assertEquals("适合 软件工程 专业", paths.get(0).get("description"));
        // 三个 pathType 都调用了一次 findTemplate
        verify(fallbackRuleEngine, times(3)).findTemplate(any(), any());
    }

    private RuleConfig buildTemplate() {
        RuleConfig rule = new RuleConfig();
        rule.setRuleKey("fallback_test");
        rule.setActionExpression("{\"pathName\":\"💼 测试路径\",\"description\":\"适合 {major} 专业\"}");
        return rule;
    }
}
