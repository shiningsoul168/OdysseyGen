package com.odysseygen.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterServiceImplTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;

    private RedisRateLimiterServiceImpl rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RedisRateLimiterServiceImpl(redisTemplate);
    }

    @Test
    void testAllowedWhenScriptReturnsOne() {
        stubScriptResult(1L);
        assertTrue(rateLimiterService.tryAcquire(1L));
    }

    @Test
    void testDeniedWhenScriptReturnsZero() {
        stubScriptResult(0L);
        assertFalse(rateLimiterService.tryAcquire(1L));
    }

    @Test
    void testFailOpenWhenRedisUnavailable() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(),
                anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        // Redis 故障时 fail-open 放行，避免限流组件拖垮整个接口
        assertTrue(rateLimiterService.tryAcquire(1L));
    }

    @Test
    void testScriptInvokedWithUserScopedKey() {
        stubScriptResult(1L);
        rateLimiterService.tryAcquire(42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), captor.capture(),
                anyString(), anyString(), anyString(), anyString());
        assertTrue(captor.getValue().get(0).startsWith("rate:limit:42"));
    }

    /** Lua 脚本固定 4 个 ARGV（窗口、上限、时间戳、member），显式逐个匹配 */
    private void stubScriptResult(Long result) {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(result);
    }
}
