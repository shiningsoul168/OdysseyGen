package com.odysseygen.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Aspect
@Component
@Slf4j
public class ControllerLogAspect {

    private static final long SLOW_THRESHOLD = 3000;

    @Pointcut("execution(* com.odysseygen.controller..*.*(..))")
    public void controllerMethod() {}

    @Around("controllerMethod()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put("traceId", traceId);

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String methodName = joinPoint.getSignature().toShortString();
        String requestUrl = request != null ? request.getRequestURI() : "unknown";
        String httpMethod = request != null ? request.getMethod() : "unknown";

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - startTime;

            if (cost > SLOW_THRESHOLD) {
                log.warn("🐢 【慢接口】{} {} {} 耗时: {}ms", methodName, httpMethod, requestUrl, cost);
            } else {
                log.info("⬅️ 【响应】{} {} {} 耗时: {}ms", methodName, httpMethod, requestUrl, cost);
            }
            return result;

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("❌ 【异常】{} {} {} 耗时: {}ms", methodName, httpMethod, requestUrl, cost, e);
            throw e;

        } finally {
            MDC.clear();
        }
    }
}