package com.odysseygen.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class MilestoneProgressResponse {

    private Long trackingId;
    private Long planId;
    private Integer pathType;
    private String pathName;
    private Integer totalMilestones;
    private Integer completedMilestones;
    private Integer progressPercent;
    private String startedAt;     // ✅ 新增
    private String completedAt;
    private List<MilestoneItem> milestones;

    @Data
    public static class MilestoneItem {
        private Long id;
        private Integer nodeIndex;
        private String nodeName;
        private String nodeDeadline;
        private Integer status;
        private String completedAt;
    }
}