package com.eneshasanbese.controller;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.eneshasanbese.dto.ApiErrorDto;

/** Hataları arayüzün gösterebileceği düz JSON'a çevirir. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorDto> notFound(NoSuchElementException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDto> badRequest(IllegalArgumentException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorDto> conflict(IllegalStateException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage());
    }

    /** Bilinmeyen yol — genel 500'e düşmesin. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorDto> noResource(NoResourceFoundException exception) {
        return build(HttpStatus.NOT_FOUND, "Böyle bir uç nokta yok: " + exception.getResourcePath());
    }

    /** Bozuk veya eksik JSON gövdesi istemci hatasıdır. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorDto> unreadableBody(HttpMessageNotReadableException exception) {
        log.warn("Okunamayan istek gövdesi: {}", exception.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "İstek gövdesi okunamadı. Gövdenin geçerli UTF-8 JSON olduğundan emin olun.");
    }

    /**
     * Çoğunlukla benzersizlik ihlali. Seed verisi id'leri açıkça yazdığı için
     * identity sequence geride kalmışsa buraya düşülür; çözüm seed dosyasının
     * sonundaki setval satırlarını çalıştırmaktır.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDto> dataIntegrity(DataIntegrityViolationException exception) {
        log.error("Veri bütünlüğü ihlali", exception);
        return build(HttpStatus.CONFLICT, "Kayıt veritabanı kısıtlarına takıldı.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> unexpected(Exception exception) {
        log.error("Beklenmeyen hata", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Sunucuda beklenmeyen bir hata oluştu.");
    }

    private ResponseEntity<ApiErrorDto> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorDto(status.value(), status.getReasonPhrase(), message));
    }
}
