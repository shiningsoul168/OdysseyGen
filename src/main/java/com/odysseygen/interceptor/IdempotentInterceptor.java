package com.odysseygen.interceptor;

import com.odysseygen.annotation.Idempotent;
import com.odysseygen.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotentInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非控制器方法直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 检查是否有 @Idempotent 注解
        Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);
        if (idempotent == null) {
            return true;
        }

        // 获取幂等 Key（前端在 Header 中传递）
        String idempotentKey = request.getHeader("Idempotent-Key");
        if (idempotentKey == null || idempotentKey.isEmpty()) {
            throw new BusinessException("缺少 Idempotent-Key 请求头");
        }

        // 从请求中提取用户标识（userId）
        Long userId = (Long) request.getAttribute("userId");
        String key = idempotent.prefix() + userId + ":" + idempotentKey;

        // 尝试存入 Redis（SETNX 原子操作）
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(idempotent.ttl()));

        if (success == null || !success) {
            log.warn("重复请求拦截: userId={}, key={}", userId, key);
            throw new BusinessException("请求正在处理中，请勿重复提交");
        }

        log.debug("幂等 Key 已锁定: {}", key);
        return true;
    }
}