package com.shortlink.core.config;

import com.shortlink.common.exception.BizException;
import com.shortlink.common.result.Result;
import com.shortlink.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("???? [{}] {} -> code={}, msg={}",
            request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("???? [{}] {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, "??????: " + e.getParameterName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("???? [{}] {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("???? [{}] {}", request.getMethod(), request.getRequestURI(), e);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }
}
