package com.odysseygen.service.rule;

import java.util.Map;

/**
 * 规则引擎接口
 * 支持薪资计算、过滤、推荐等多种规则类型
 */
public interface RuleEngine {

    /**
     * 执行规则
     *
     * @param ruleType 规则类型: SALARY / FILTER / RECOMMEND
     * @param context  上下文参数
     * @return 执行结果（薪资系数 / 过滤结果 / 推荐分数）
     */
    double evaluate(String ruleType, Map<String, Object> context);

    /**
     * 执行规则（带默认值）
     */
    double evaluate(String ruleType, Map<String, Object> context, double defaultValue);

    /**
     * 刷新规则缓存
     */
    void refreshCache();
}
