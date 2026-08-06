package com.odysseygen.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserInfoDTO {
    private Long userId;
    private String username;
    private String email;
    private String avatar;
    private String realName;
    private String phone;
    private String major;
    private Double gpa;
    private Integer schoolLevel;
    private Integer englishLevel;
    private Integer graduationYear;
    private String personalityTags;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
