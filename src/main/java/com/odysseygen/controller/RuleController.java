package com.odysseygen.controller;

import com.odysseygen.common.Result;
import com.odysseygen.dto.request.RuleConfigRequest;
import com.odysseygen.dto.response.RuleConfigResponse;
import com.odysseygen.service.RuleConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rules")
@RequiredArgsConstructor
@Slf4j
public class RuleController {

    private final RuleConfigService ruleConfigService;

    /**
     * 查询所有规则
     */
    @GetMapping
    public Result<List<RuleConfigResponse>> listAll() {
        List<RuleConfigResponse> rules = ruleConfigService.listAll();
        return Result.success(rules);
    }

    /**
     * 根据ID查询规则
     */
    @GetMapping("/{ruleId}")
    public Result<RuleConfigResponse> getById(@PathVariable Integer ruleId) {
        RuleConfigResponse rule = ruleConfigService.getById(ruleId);
        return Result.success(rule);
    }

    /**
     * 新增规则
     */
    @PostMapping
    public Result<RuleConfigResponse> create(@Valid @RequestBody RuleConfigRequest request) {
        RuleConfigResponse rule = ruleConfigService.create(request);
        return Result.success("新增规则成功", rule);
    }

    /**
     * 更新规则
     */
    @PutMapping("/{ruleId}")
    public Result<RuleConfigResponse> update(@PathVariable Integer ruleId,
                                             @Valid @RequestBody RuleConfigRequest request) {
        RuleConfigResponse rule = ruleConfigService.update(ruleId, request);
        return Result.success("更新规则成功", rule);
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{ruleId}")
    public Result<?> delete(@PathVariable Integer ruleId) {
        ruleConfigService.delete(ruleId);
        return Result.success("删除规则成功", null);
    }

    /**
     * 刷新规则缓存
     */
    @PostMapping("/refresh")
    public Result<?> refreshCache() {
        ruleConfigService.refreshCache();
        return Result.success("规则缓存刷新成功", null);
    }
}
