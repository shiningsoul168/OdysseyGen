package com.odysseygen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.odysseygen.common.BusinessException;
import com.odysseygen.common.ResultCode;
import com.odysseygen.dto.request.RuleConfigRequest;
import com.odysseygen.dto.response.RuleConfigResponse;
import com.odysseygen.entity.RuleConfig;
import com.odysseygen.mapper.RuleConfigMapper;
import com.odysseygen.service.RuleConfigService;
import com.odysseygen.service.rule.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleConfigServiceImpl implements RuleConfigService {

    private final RuleConfigMapper ruleConfigMapper;
    private final RuleEngine ruleEngine;
    private final ObjectMapper objectMapper;

    @Override
    public List<RuleConfigResponse> listAll() {
        List<RuleConfig> rules = ruleConfigMapper.selectList(null);
        return convertToResponseList(rules);
    }

    @Override
    public RuleConfigResponse getById(Integer ruleId) {
        RuleConfig rule = ruleConfigMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(404, "规则不存在");
        }
        return convertToResponse(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleConfigResponse create(RuleConfigRequest request) {
        // 1. 检查 ruleKey 是否已存在
        LambdaQueryWrapper<RuleConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleConfig::getRuleKey, request.getRuleKey());
        if (ruleConfigMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("规则Key已存在: " + request.getRuleKey());
        }

        // 2. 校验 JSON 格式
        validateJson(request.getConditionExpression(), request.getActionExpression());

        // 3. 保存
        RuleConfig rule = new RuleConfig();
        BeanUtils.copyProperties(request, rule);
        rule.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleConfigMapper.insert(rule);

        // 4. 刷新缓存
        ruleEngine.refreshCache();

        log.info("新增规则成功: ruleKey={}", rule.getRuleKey());
        return convertToResponse(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleConfigResponse update(Integer ruleId, RuleConfigRequest request) {
        // 1. 检查是否存在
        RuleConfig existing = ruleConfigMapper.selectById(ruleId);
        if (existing == null) {
            throw new BusinessException(404, "规则不存在");
        }

        // 2. 检查 ruleKey 是否被其他规则占用
        LambdaQueryWrapper<RuleConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleConfig::getRuleKey, request.getRuleKey())
                .ne(RuleConfig::getRuleId, ruleId);
        if (ruleConfigMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("规则Key已存在: " + request.getRuleKey());
        }

        // 3. 校验 JSON 格式
        validateJson(request.getConditionExpression(), request.getActionExpression());

        // 4. 更新
        BeanUtils.copyProperties(request, existing);
        existing.setUpdatedAt(LocalDateTime.now());
        ruleConfigMapper.updateById(existing);

        // 5. 刷新缓存
        ruleEngine.refreshCache();

        log.info("更新规则成功: ruleId={}, ruleKey={}", ruleId, existing.getRuleKey());
        return convertToResponse(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer ruleId) {
        RuleConfig rule = ruleConfigMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(404, "规则不存在");
        }
        ruleConfigMapper.deleteById(ruleId);
        ruleEngine.refreshCache();
        log.info("删除规则成功: ruleId={}, ruleKey={}", ruleId, rule.getRuleKey());
    }

    @Override
    public void refreshCache() {
        ruleEngine.refreshCache();
        log.info("规则缓存已刷新（通过管理接口）");
    }

    /**
     * 校验 JSON 格式
     */
    private void validateJson(String conditionExpression, String actionExpression) {
        try {
            objectMapper.readTree(conditionExpression);
        } catch (Exception e) {
            throw new BusinessException("条件表达式 JSON 格式错误: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
        try {
            objectMapper.readTree(actionExpression);
        } catch (Exception e) {
            throw new BusinessException("动作表达式 JSON 格式错误: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    private RuleConfigResponse convertToResponse(RuleConfig rule) {
        RuleConfigResponse response = new RuleConfigResponse();
        BeanUtils.copyProperties(rule, response);
        return response;
    }

    private List<RuleConfigResponse> convertToResponseList(List<RuleConfig> rules) {
        List<RuleConfigResponse> list = new ArrayList<>();
        for (RuleConfig rule : rules) {
            list.add(convertToResponse(rule));
        }
        return list;
    }
}