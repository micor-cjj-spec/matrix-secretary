package com.kailei.demo.channel.model;

import java.util.Locale;

public enum ChannelPlatform {
    FEISHU,
    DINGTALK,
    QQ,
    WEB;

    public static ChannelPlatform from(String value) {
        if (value == null || value.isBlank()) {
            return WEB;
        }
        return ChannelPlatform.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
