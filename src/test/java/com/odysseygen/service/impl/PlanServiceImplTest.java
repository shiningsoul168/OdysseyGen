package com.odysseygen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseygen.common.BusinessException;
import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.dto.response.PathResponse;
import com.odysseygen.mapper.PathDetailMapper;
import com.odysseygen.mapper.PlanRecordMapper;
import com.odysseygen.service.AiGenerateTaskService;
import com.odysseygen.service.FallbackService;
import com.odysseygen.service.PlanPersistenceService;
import com.odysseygen.util.CacheKeyUtil;
import com.odysseygen.util.DeepSeekUtil;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceImplTest {

    @Mock private CacheKeyUtil cacheKeyUtil;
    @Mock private PlanRecordMapper planRecordMapper;
    @Mock private PathDetailMapper pathDetailMapper;
    @Mock private DeepSeekUtil deepSeekUtil;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private CircuitBreaker aiCircuitBreaker;
    @Mock private AiGenerateTaskService aiGenerateTaskService;
    @Mock private PlanPersistenceService planPersistenceService;
    @Mock private FallbackService fallbackService;
    @Mock private ValueOperations<String, Object> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PlanServiceImpl planService;

    private static final String CACHE_KEY = "plan:cache:test";
    private static final String AI_RESPONSE = "{\"paths\":[{\"pathName\":\"测试路径\"}]}";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cacheKeyUtil.generateCacheKey(any())).thenReturn(CACHE_KEY);
        planService = new PlanServiceImpl(
                cacheKeyUtil, planRecordMapper, pathDetailMapper, deepSeekUtil,
                objectMapper, redisTemplate, aiCircuitBreaker,
                aiGenerateTaskService, planPersistenceService, fallbackService);
    }

    @Test
    void testCacheHitShouldPersistWithoutCallingAi() throws Exception {
        ProfileRequest request = new ProfileRequest();
        request.setGoalType(1);
        request.setMajor("软件工程");

        when(valueOperations.get(CACHE_KEY)).thenReturn(AI_RESPONSE);
        PathResponse expected = new PathResponse();
        expected.setPlanId(100L);
        when(planPersistenceService.savePlan(any(), any(), any())).thenReturn(100L);
        when(planPersistenceService.buildResponse(any(), any(), any())).thenReturn(expected);

        PathResponse result = planService.generatePlan(1L, request);

        assertEquals(100L, result.getPlanId());
        verify(planPersistenceService, times(1)).savePlan(any(), any(), any());
        verify(planPersistenceService, times(1)).buildResponse(any(), any(), any());
        verify(deepSeekUtil, never()).generatePaths(any(), any(), any(), any());
    }

    @Test
    void testCacheMissShouldCallAiAndPersist() throws Exception {
        ProfileRequest request = new ProfileRequest();
        request.setGoalType(1);
        request.setMajor("软件工程");

        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any())).thenReturn(true);
        when(deepSeekUtil.generatePaths(any(), any(), any(), any())).thenReturn(AI_RESPONSE);
        PathResponse expected = new PathResponse();
        expected.setPlanId(200L);
        when(planPersistenceService.savePlan(any(), any(), any())).thenReturn(200L);
        when(planPersistenceService.buildResponse(any(), any(), any())).thenReturn(expected);

        PathResponse result = planService.generatePlan(1L, request);

        assertEquals(200L, result.getPlanId());
        verify(deepSeekUtil, times(1)).generatePaths(any(), any(), any(), any());
        verify(valueOperations, times(1)).set(eq(CACHE_KEY), eq(AI_RESPONSE), any());
    }

    @Test
    void testLockContentionShouldThrowBusy() {
        ProfileRequest request = new ProfileRequest();
        request.setGoalType(1);
        request.setMajor("软件工程");

        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any())).thenReturn(false);

        assertThrows(BusinessException.class, () -> planService.generatePlan(1L, request));
    }
}
