package com.odysseygen.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseygen.common.BusinessException;
import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.entity.RuleConfig;
import com.odysseygen.service.rule.impl.FallbackRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 兜底服务：AI 不可用时，用规则引擎按画像匹配兜底模板，填充画像数据后生成三条路径。
 * 产出的 paths 结构与 AI 返回一致，下游直接复用 savePlan + buildResponse 落库和构建响应。
 * salaryExpectation 不在此处填充——由 PlanPersistenceService.resolveSalary 走薪资规则引擎兜底。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FallbackService {

    private final FallbackRuleEngine fallbackRuleEngine;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> generatePaths(ProfileRequest request) throws Exception {
        List<Map<String, Object>> paths = new ArrayList<>();
        for (int pathType = 1; pathType <= 3; pathType++) {
            RuleConfig template = fallbackRuleEngine.findTemplate(request.getGoalType(), pathType);
            if (template == null || template.getActionExpression() == null) {
                throw new BusinessException("兜底模板缺失: goalType=" + request.getGoalType() + ", pathType=" + pathType);
            }
            String filled = fillPlaceholders(template.getActionExpression(), request);
            Map<String, Object> pathMap = objectMapper.readValue(filled, new TypeReference<>() {});
            paths.add(pathMap);
        }
        return paths;
    }

    private String fillPlaceholders(String template, ProfileRequest request) {
        return template
                .replace("{major}", request.getMajor() != null ? request.getMajor() : "")
                .replace("{graduationYear}", request.getGraduationYear() != null ? String.valueOf(request.getGraduationYear()) : "");
    }
}
