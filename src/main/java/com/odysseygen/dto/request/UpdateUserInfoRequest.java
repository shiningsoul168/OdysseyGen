package com.odysseygen.dto.request;

import lombok.Data;

@Data
public class UpdateUserInfoRequest {

    private String major;
    private Double gpa;
    private Integer schoolLevel;
    private Integer englishLevel;
    private Integer graduationYear;
    private String[] personalityTags;
}
