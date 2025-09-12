package com.algomeet.notificationservice.enums;
public enum ClientPlatform {
    ANDROID,
    IOS,
    WEB,
    HARMONYOS;

    public static ClientPlatform fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Platform cannot be null");
        }
        return ClientPlatform.valueOf(value.toUpperCase());
    }
}