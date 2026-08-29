package com.odysseygen.config;

import com.odysseygen.interceptor.IdempotentInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final IdempotentInterceptor idempotentInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 认证与角色校验已由 Spring Security JWT 过滤器完成，这里只保留幂等拦截器
        registry.addInterceptor(idempotentInterceptor)
                .addPathPatterns("/api/plan/generate-async");
    }
}
