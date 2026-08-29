package com.odysseygen.enums;

import lombok.Getter;

@Getter
public enum RuleTypeEnum {
    FILTER(1, "硬性过滤"),
    RECOMMEND(2, "软性扣分"),
    SALARY(3, "薪资加权"),
    FALLBACK(4, "兜底策略");

    private final Integer code;
    private final String desc;

    RuleTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RuleTypeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (RuleTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
