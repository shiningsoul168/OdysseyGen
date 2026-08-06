package com.odysseygen.service;

import com.odysseygen.dto.request.RuleConfigRequest;
import com.odysseygen.dto.response.RuleConfigResponse;
import com.odysseygen.entity.RuleConfig;

import java.util.List;

public interface RuleConfigService {

    /**
     * 查询所有规则
     */
    List<RuleConfigResponse> listAll();

    /**
     * 根据ID查询规则
     */
    RuleConfigResponse getById(Integer ruleId);

    /**
     * 新增规则
     */
    RuleConfigResponse create(RuleConfigRequest request);

    /**
     * 更新规则
     */
    RuleConfigResponse update(Integer ruleId, RuleConfigRequest request);

    /**
     * 删除规则
     */
    void delete(Integer ruleId);

    /**
     * 刷新规则缓存
     */
    void refreshCache();
}
