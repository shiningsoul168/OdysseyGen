package com.odysseygen.constant;

import java.time.Duration;

public class CacheConstants {

    public static final String CACHE_PREFIX = "plan:cache:";

    public static final Duration CACHE_TTL = Duration.ofHours(24);

    public static final String LOCK_SUFFIX = ":lock";

    public static final Duration LOCK_TTL = Duration.ofMinutes(3);
}
