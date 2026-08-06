package com.odysseygen.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等 Key 的前缀
     */
    String prefix() default "idempotent.js:";

    /**
     * 幂等过期时间（秒），默认 5 分钟
     */
    long ttl() default 300;
}
