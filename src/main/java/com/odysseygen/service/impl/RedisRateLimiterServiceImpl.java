package com.odysseygen.service.impl;

import com.odysseygen.constant.CacheConstants;
import com.odysseygen.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

/**
 * 分布式限流（Redis + Lua 滑动窗口），替代原单机内存限流（MemoryRateLimiterServiceImpl）。
 *
 * 为什么升级：
 * - 原实现用 Caffeine 存在 JVM 内存中，多实例部署时每个实例独立计数，限流总量会放大 N 倍；
 * - 本实现用 Redis ZSET 记录每个用户的请求时间戳，Lua 脚本内原子完成
 *   "清理窗口外记录 + 计数判断 + 写入本次请求"，跨实例共享同一个计数，语义准确。
 *
 * 滑动窗口算法：窗口大小 1 分钟、上限 3 次（与原单机实现语义一致）。
 * 相比固定窗口（INCR + EXPIRE）避免了窗口边界处的突发翻倍问题。
 *
 * 故障策略：Redis 不可用时 fail-open（放行并告警）——限流组件不应成为单点故障；
 * 代价是 Redis 故障期间失去限流保护（可配合入口幂等/熔断兜底）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisRateLimiterServiceImpl implements RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 滑动窗口大小（毫秒）：1 分钟 */
    private static final long WINDOW_MS = 60_000L;

    /** 窗口内最大请求数 */
    private static final long LIMIT = 3L;

    /**
     * 滑动窗口 Lua 脚本（原子执行）：
     * 1. ZREMRANGEBYSCORE 清理窗口外的旧请求记录（score 即请求时间戳）
     * 2. ZCARD 统计当前窗口内请求数，达到上限直接返回 0（拒绝）
     * 3. ZADD 记录本次请求，PEXPIRE 刷新 TTL（空闲后自动过期，避免残留）
     * 注意：member 必须唯一（调用方传 UUID），否则同一毫秒内两次请求会被 ZSET 去重。
     */
    private static final String SCRIPT = """
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local member = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
            local count = redis.call('ZCARD', KEYS[1])
            if count >= limit then
                return 0
            end
            redis.call('ZADD', KEYS[1], now, member)
            redis.call('PEXPIRE', KEYS[1], window)
            return 1
            """;

    private final DefaultRedisScript<Long> rateLimitScript = new DefaultRedisScript<>(SCRIPT, Long.class);

    @Override
    public boolean tryAcquire(Long userId) {
        String key = CacheConstants.RATE_LIMIT_PREFIX + userId;
        Long result;
        try {
            result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(WINDOW_MS),
                    String.valueOf(LIMIT),
                    String.valueOf(System.currentTimeMillis()),
                    UUID.randomUUID().toString());
        } catch (Exception e) {
            // Redis 不可用时 fail-open：限流组件不能成为单点故障（代价是故障期间失去限流保护）
            log.warn("Redis 限流不可用，fail-open 放行: userId={}, 错误: {}", userId, e.getMessage());
            return true;
        }

        boolean allowed = Long.valueOf(1L).equals(result);
        log.info("限流检查: userId={}, 结果={}", userId, allowed);
        return allowed;
    }
}
