package com.odysseygen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_profiles")
public class UserProfile {

    @TableId(type = IdType.AUTO)
    private Long profileId;

    private Long userId;

    private Integer goalType;  // 1-就业 2-考研 3-考公

    private String major;

    private Double gpa;

    private Integer schoolLevel;  // 1-985/211 2-双一流 3-普通本科 4-专科

    private Integer englishLevel;  // 1-CET4 2-CET6 3-雅思/托福 4-无

    private Boolean isPartyMember;

    private Integer graduationYear;

    private String goalSpecificData;  // JSON 字符串（存储就业/考研/考公专属字段）

    private String personalityTags;   // JSON 数组

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
