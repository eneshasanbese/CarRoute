package com.eneshasanbese.dto;

public record ApiErrorDto(int status, String error, String message) {
}
