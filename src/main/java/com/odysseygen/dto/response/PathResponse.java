package com.odysseygen.dto.response;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PathResponse {
    private Long planId;
    private List<PathItem> paths;

    @Data
    public static class PathItem {
        private Integer pathType;
        private String pathName;
        private String pathSummary;
        private String description;
        private List<Map<String, String>> timeline;
        private List<Map<String, String>> keyNodes;
        private List<String> skillGap;
        private SalaryExpectation salaryExpectation;  // ✅ 新增
        private List<String> riskFactors;
        private List<String> recommendedActions;
        private StopLossAdvice stopLossAdvice;
    }

    @Data
    public static class SalaryExpectation {
        private Integer entry;    // 入职/初始
        private Integer mid;      // 中期
        private Integer senior;   // 资深
    }

    @Data
    public static class StopLossAdvice {
        private String trigger;
        private String action;
        private String deadline;
        private String alternativePath;
    }
}
