package com.odysseygen.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.odysseygen.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MemoryRateLimiterServiceImpl implements RateLimiterService {

    private final Cache<Long, UserRateRecord> userLimiters = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private static final int LIMIT = 3;
    private static final Duration PERIOD = Duration.ofMinutes(1);

    @Override
    public boolean tryAcquire(Long userId) {
        UserRateRecord record = userLimiters.get(userId, key -> new UserRateRecord());
        boolean result = record.tryAcquire();
        log.info("===== 限流检查: userId={}, 结果={}, 当前记录数={} =====",
                userId, result, record.getRequestTimesCount());
        return result;
    }

    private static class UserRateRecord {
        private final ConcurrentLinkedQueue<LocalDateTime> requestTimes = new ConcurrentLinkedQueue<>();

        public synchronized boolean tryAcquire() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minus(PERIOD);

            log.info("===== 限流前: 队列大小={}, 截止时间={} =====", requestTimes.size(), cutoff);

            while (!requestTimes.isEmpty() && requestTimes.peek().isBefore(cutoff)) {
                requestTimes.poll();
            }

            log.info("===== 清理后: 队列大小={} =====", requestTimes.size());

            if (requestTimes.size() >= LIMIT) {
                log.info("===== ❌ 限流触发！=====");
                return false;
            }

            requestTimes.offer(now);
            log.info("===== ✅ 请求通过，记录时间戳 =====");
            return true;
        }

        // ✅ 新增：获取当前请求记录数量（仅用于日志调试）
        public int getRequestTimesCount() {
            return requestTimes.size();
        }
    }
}