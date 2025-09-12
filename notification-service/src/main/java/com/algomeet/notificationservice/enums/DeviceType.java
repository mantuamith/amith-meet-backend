package com.algomeet.notificationservice.enums;
public enum DeviceType {
    ANDROID,
    IOS,
    WEB;

    public static DeviceType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Device type cannot be null");
        }
        return DeviceType.valueOf(value.toUpperCase());
    }
}