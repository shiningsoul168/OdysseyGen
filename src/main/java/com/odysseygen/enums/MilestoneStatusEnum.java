package com.odysseygen.enums;

import lombok.Getter;

@Getter
public enum MilestoneStatusEnum {
    NOT_STARTED(0, "未开始"),
    IN_PROGRESS(1, "进行中"),
    COMPLETED(2, "已完成");

    private final Integer code;
    private final String label;

    MilestoneStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static MilestoneStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (MilestoneStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
