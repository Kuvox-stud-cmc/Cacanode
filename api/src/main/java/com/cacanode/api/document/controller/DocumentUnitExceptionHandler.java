package com.cacanode.api.document.controller;

import com.cacanode.api.chat.exception.ChatApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = DocumentController.class)
public class DocumentUnitExceptionHandler {
    @ExceptionHandler(ChatApiException.class)
    public ResponseEntity<Map<String, String>> handle(ChatApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("detail", exception.getMessage()));
    }
}
