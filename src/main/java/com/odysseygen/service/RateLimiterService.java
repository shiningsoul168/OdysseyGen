package com.odysseygen.service;

public interface RateLimiterService {
    boolean tryAcquire(Long userId);
}
