package com.odysseygen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("path_details")
public class PathDetail {

    @TableId(type = IdType.AUTO)
    private Long pathId;

    private Long planId;

    private Integer pathType;  // 1-主流 2-备用 3-理想

    private String pathName;

    private String pathSummary;

    private String description;

    private String timeline;   // JSON

    private String keyNodes;   // JSON

    private String skillGap;   // JSON

    private String salaryExpectation;  // JSON

    private String riskFactors;        // JSON

    private String recommendedActions; // JSON

    private String stopLossAdvice;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
