package com.odysseygen.enums;

import lombok.Getter;

@Getter
public enum TrackingStatusEnum {
    IN_PROGRESS(1, "进行中"),
    COMPLETED(2, "已完成"),
    ABANDONED(3, "已放弃");

    private final Integer code;
    private final String label;

    TrackingStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static TrackingStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (TrackingStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public static String labelOf(Integer code) {
        TrackingStatusEnum e = fromCode(code);
        return e != null ? e.getLabel() : "未知";
    }
}
