package com.odysseygen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_info")
public class UserInfo {

    @TableId(type = IdType.AUTO)
    private Long userId;

    private String username;

    private String password;

    private String email;

    private String avatar;

    private String realName;

    private String phone;

    // ====== 新增：公共字段（注册时采集，首页自动回填） ======
    private String major;                 // 专业名称
    private Double gpa;                   // GPA
    private Integer schoolLevel;          // 学校层次: 1-985/211 2-双一流 3-普通本科 4-专科
    private Integer englishLevel;         // 英语水平: 1-CET-4 2-CET-6 3-雅思/托福 4-无
    private Integer graduationYear;       // 预计毕业年份
    private String personalityTags;       // 性格标签（JSON数组）

    private Integer status;               // 1-正常 0-禁用

    private String role;                  // 角色：USER / ADMIN

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
