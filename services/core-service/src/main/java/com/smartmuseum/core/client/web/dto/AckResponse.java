package com.smartmuseum.core.client.web.dto;

public record AckResponse(String status) {
    public static AckResponse accepted() {
        return new AckResponse("accepted");
    }
} 