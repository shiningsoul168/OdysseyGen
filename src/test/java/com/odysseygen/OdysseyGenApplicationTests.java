package com.odysseygen;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载冒烟测试。
 * jwt.secret 通过 properties 覆盖为有效值（≥32 字节），
 * 满足 JwtUtil 的启动期密钥强度校验，避免依赖外部环境变量。
 */
@SpringBootTest(properties = {
        "jwt.secret=test-only-secret-0123456789abcdefghijklmnopqrstuvwxyz-ABCDEF"
})
class OdysseyGenApplicationTests {

    @Test
    void contextLoads() {
    }

}
