package org.lixiyun.common.core.error.handler;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.result.Result;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@Hidden
@RestControllerAdvice
public class ExceptionGlobalHandler {

    /**
     * 业务异常处理器
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.error("请求路径：{}， 业务异常：{}", request.getRequestURI(), e.toString() + ":" + e.getMessage());
        return Result.error(e.getCode(), e.getMsg());
    }

    /**
     * 兜底异常处理器
     */
    @ExceptionHandler(Throwable.class)
    public Result<?> handleException(Throwable e, HttpServletRequest request) {
        log.error("请求路径：{}， 系统异常：{}", request.getRequestURI(), e);
        return Result.error(SystemExceptionEnum.SYSTEM_ERROR);
    }

    /**
     * 参数为空处理器
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.error("请求路径：{}， 参数为空：{}", request.getRequestURI(), e.toString() + ":" + e.getMessage());
        return Result.error(SystemExceptionEnum.PARAM_ILLEGAL);
    }

    /**
     * 数据绑定失败处理器
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleConstraintViolationException(BindException e, HttpServletRequest request) {
        log.error("请求路径：{}， 数据绑定失败：{}", request.getRequestURI(), e.toString() + ":" + e.getMessage());
        return Result.error(SystemExceptionEnum.PARAM_ERROR);
    }

    /**
     * 参数验证失败处理器（处理JSR-303约束验证和Spring MVC参数验证）
     */
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public Result<?> handleValidationException(Exception e, HttpServletRequest request) {
        log.error("请求路径：{}， 参数验证失败：{}", request.getRequestURI(), e.toString() + ":" + e.getMessage());

        String message;
        if (e instanceof ConstraintViolationException constraintViolationException) {
            message = constraintViolationException.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
        } else if (e instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            message = methodArgumentNotValidException.getBindingResult().getFieldErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .collect(Collectors.joining("; "));
        } else {
            message = e.getMessage();
        }

        return Result.error(SystemExceptionEnum.PARAM_ILLEGAL.getCode(), message);
    }

}
