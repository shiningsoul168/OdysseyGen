package com.odysseygen.constant;

import java.time.Duration;

public class CacheConstants {

    public static final String CACHE_PREFIX = "plan:cache:";

    public static final Duration CACHE_TTL = Duration.ofHours(24);

    public static final String LOCK_SUFFIX = ":lock";

    public static final Duration LOCK_TTL = Duration.ofMinutes(3);

    /** 异步任务状态 Key 前缀 */
    public static final String TASK_PREFIX = "plan:task:";

    /** 异步任务状态 TTL */
    public static final Duration TASK_TTL = Duration.ofMinutes(5);

    /** 分布式限流 Key 前缀（Redis ZSET 滑动窗口） */
    public static final String RATE_LIMIT_PREFIX = "rate:limit:";
}
