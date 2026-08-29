package com.odysseygen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseygen.constant.CacheConstants;
import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.dto.response.PathResponse;
import com.odysseygen.dto.response.TaskResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * AI 异步生成任务执行器。
 * 单独拆成一个 Bean，是为了让 @Async 通过 Spring 代理生效：
 * 在同一个类里自调用（this.xxx()）不会走 Spring 代理，@Async 会静默失效。
 * 这里用 @Lazy 注入 PlanService，是为了打断 PlanServiceImpl <-> AiGenerateTaskService 的构造循环依赖。
 */
@Service
@Slf4j
public class AiGenerateTaskService {

    private final PlanService planService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public AiGenerateTaskService(@Lazy PlanService planService,
                                 RedisTemplate<String, Object> redisTemplate,
                                 ObjectMapper objectMapper) {
        this.planService = planService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Async("taskExecutor")
    public void executeAsync(Long userId, ProfileRequest request, String taskId) {
        String redisKey = CacheConstants.TASK_PREFIX + taskId;
        try {
            PathResponse result = planService.generatePlanWithBreaker(userId, request);
            updateStatus(redisKey, task -> {
                task.setStatus("SUCCESS");
                task.setResult(result);
            });
        } catch (Exception e) {
            log.error("AI 生成失败，taskId: {}", taskId, e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            updateStatus(redisKey, task -> {
                task.setStatus("FAILED");
                task.setError("AI 服务暂时不可用: " + errorMsg);
            });
        }
    }

    private void updateStatus(String redisKey, Consumer<TaskResponse> updater) {
        try {
            String json = (String) redisTemplate.opsForValue().get(redisKey);
            if (json == null) {
                log.warn("任务状态不存在或已过期: {}", redisKey);
                return;
            }
            TaskResponse task = objectMapper.readValue(json, TaskResponse.class);
            updater.accept(task);
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(task), CacheConstants.TASK_TTL);
        } catch (Exception e) {
            log.error("更新任务状态失败: {}", redisKey, e);
        }
    }
}
