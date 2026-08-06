package com.odysseygen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("plan_records")
public class PlanRecord {

    @TableId(type = IdType.AUTO)
    private Long planId;

    private Long userId;

    private Long profileId;

    private Integer goalType;  // 1-就业 2-考研 3-考公

    private String generationPrompt;

    private BigDecimal generationCost;

    private Integer responseTimeMs;

    private Boolean isFavorite;

    @TableLogic
    private Boolean isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<String> pathNames;
}