package com.odysseygen.config;

import com.odysseygen.interceptor.AuthInterceptor;
import com.odysseygen.interceptor.IdempotentInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final IdempotentInterceptor idempotentInterceptor;  // ✅ 新增

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ✅ 先认证（注入 userId）
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/user/login", "/api/user/register");

        // ✅ 后幂等（读取 userId）
        registry.addInterceptor(idempotentInterceptor)
                .addPathPatterns("/api/plan/generate-async");
    }
}