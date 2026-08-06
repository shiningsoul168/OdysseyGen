package com.odysseygen.enums;

import lombok.Getter;

@Getter
public enum GoalTypeEnum {
    EMPLOYMENT(1, "就业"),
    POSTGRADUATE(2, "考研"),
    CIVIL_SERVICE(3, "考公");

    private final Integer code;
    private final String desc;

    GoalTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static GoalTypeEnum fromCode(Integer code) {
        for (GoalTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}