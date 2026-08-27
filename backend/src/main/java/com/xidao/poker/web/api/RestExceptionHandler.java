package com.xidao.poker.web.api;

import com.xidao.poker.application.room.RoomApplicationErrorCode;
import com.xidao.poker.application.room.RoomApplicationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/** 将内部异常收敛为稳定协议，不向客户端暴露堆栈。 */
@RestControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(RoomApplicationException.class)
    public ResponseEntity<ApiError> roomError(RoomApplicationException error) {
        HttpStatus status = error.code() == RoomApplicationErrorCode.ROOM_NOT_FOUND
                ? HttpStatus.NOT_FOUND
                : HttpStatus.CONFLICT;
        return error(status, error.code().name(), error.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> invalidRequest(Exception ignored) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "request validation failed");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, Instant.now()));
    }
}
