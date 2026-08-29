package com.odysseygen.service;

import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.enums.GoalTypeEnum;
import com.odysseygen.service.rule.impl.SalaryRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 薪资 / 预期值计算组件。
 * - 就业: 单位为 K/月，结合规则引擎系数 + 城市系数
 * - 考研: entry/mid/senior 表示预估排名百分位（越小越好）
 * - 考公: entry/mid/senior 表示预估职级
 */
@Component
@RequiredArgsConstructor
public class SalaryCalculator {

    private final SalaryRuleEngine salaryRuleEngine;

    public Map<String, Integer> calculate(Integer goalType, Integer pathType, ProfileRequest request) {
        // ====== 考研方向 ======
        if (GoalTypeEnum.POSTGRADUATE.matches(goalType)) {
            double gpaFactor = request.getGpa() != null ? Math.max(0.7, request.getGpa() / 4.0) : 0.85;
            double schoolFactor = switch (request.getSchoolLevel() != null ? request.getSchoolLevel() : 3) {
                case 1 -> 0.70;
                case 2 -> 0.85;
                default -> 1.0;
            };
            double score = gpaFactor * schoolFactor;
            return Map.of(
                    "entry", (int) Math.round(50 * score),
                    "mid", (int) Math.round(25 * score),
                    "senior", (int) Math.round(10 * score)
            );
        }

        // ====== 考公方向 ======
        if (GoalTypeEnum.CIVIL_SERVICE.matches(goalType)) {
            return Map.of(
                    "entry", 1,
                    "mid", 3 + (Boolean.TRUE.equals(request.getIsPartyMember()) ? 1 : 0),
                    "senior", 6 + (request.getSchoolLevel() != null && request.getSchoolLevel() <= 2 ? 2 : 0)
            );
        }

        // ====== 就业方向（规则引擎 + 城市系数） ======
        Map<String, Object> context = new HashMap<>();
        context.put("goalType", goalType);
        context.put("pathType", pathType);
        context.put("schoolLevel", request.getSchoolLevel() != null ? request.getSchoolLevel() : 3);
        context.put("gpa", request.getGpa() != null ? request.getGpa() : 3.0);
        context.put("englishLevel", request.getEnglishLevel() != null ? request.getEnglishLevel() : 4);
        context.put("internshipCount", request.getGoalData() != null
                ? request.getGoalData().getOrDefault("internshipCount", 0)
                : 0);

        // 1. 规则引擎计算综合系数
        double factor = salaryRuleEngine.evaluate(context, 1.0);

        // 2. 基础薪资（按路径类型）
        int baseEntry, baseMid, baseSenior;
        switch (pathType != null ? pathType : 1) {
            case 1 -> { baseEntry = 12; baseMid = 22; baseSenior = 40; }
            case 2 -> { baseEntry = 8;  baseMid = 16; baseSenior = 28; }
            case 3 -> { baseEntry = 6;  baseMid = 18; baseSenior = 38; }
            default -> { baseEntry = 10; baseMid = 20; baseSenior = 35; }
        }

        // 3. 城市系数
        String preferredCity = request.getGoalData() != null
                ? (String) request.getGoalData().get("preferredCity")
                : null;
        double finalFactor = factor * getCityFactor(preferredCity);

        return Map.of(
                "entry", Math.max(4, (int) Math.round(baseEntry * finalFactor / 2.0) * 2),
                "mid", Math.max(8, (int) Math.round(baseMid * finalFactor / 2.0) * 2),
                "senior", Math.max(15, (int) Math.round(baseSenior * finalFactor / 2.0) * 2)
        );
    }

    private double getCityFactor(String city) {
        if (city == null || city.isEmpty()) return 1.0;
        if (city.contains("北京") || city.contains("上海") || city.contains("深圳")
                || city.contains("广州") || city.contains("杭州")) {
            return 1.0;
        } else if (city.contains("南京") || city.contains("武汉") || city.contains("成都")
                || city.contains("西安") || city.contains("苏州") || city.contains("重庆") || city.contains("天津")) {
            return 0.85;
        }
        // 其他城市
        return 0.80;
    }
}
