package com.smartmuseum.core.client.ws.dto;

public record PushMessage(
    String type,
    String deviceId,
    Object payload
) {
} 