package org.lixiyun.common.core.error.handler;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.result.Result;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Hidden
@RestControllerAdvice
public class ExceptionGlobalHandler extends ResponseEntityExceptionHandler {

    private static final int MAX_STACK_TRACE_LINES = 50;

    /**
     * 业务异常处理器（保持 HTTP 200，业务错误通过 body.code 区分）
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.error("请求路径：{}，请求方法：{}，业务异常：{}，异常信息：{}",
                request.getRequestURI(), request.getMethod(), e.toString() + ":" + e.getMessage(), getTruncatedStackTrace(e));
        return Result.error(e.getCode(), e.getMsg());
    }

    /**
     * 兜底异常处理器 → HTTP 500
     * 所有未被其他处理器捕获的异常（含框架内部错误等）统一返回 500
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<?>> handleException(Throwable e, HttpServletRequest request) {
        log.error("请求路径：{}，请求方法：{}，未捕获异常：{}",
                request.getRequestURI(), request.getMethod(), getTruncatedStackTrace(e));
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(SystemExceptionEnum.SYSTEM_ERROR));
    }

    /**
     * 参数验证失败（@Validated 在 Controller 类级别触发，非请求体校验） → HTTP 400
     * 注意：ConstraintViolationException 不在 ResponseEntityExceptionHandler 的默认处理范围内
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolationException(
            ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("请求路径：{}，请求方法：{}，参数校验失败：{}",
                request.getRequestURI(), request.getMethod(), message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, message));
    }

    // ======================== 覆盖父类方法，统一返回 Result 格式 ========================

    /**
     * HTTP 方法不支持 → 405
     */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("不支持的请求方法：{}，支持的：{}", ex.getMethod(), ex.getSupportedHttpMethods());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error(405, "请求方法不允许，支持的：" + ex.getSupportedHttpMethods()));
    }

    /**
     * 缺少请求参数 → 400
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("缺少请求参数：{}", ex.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "缺少参数：" + ex.getParameterName()));
    }

    /**
     * 请求体参数校验失败（@Valid 在 DTO 上） → 400
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败：{}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, message));
    }

    // ======================== WebSocket 异常处理（不受 HTTP 影响，保持不变） ========================

    /**
     * 全局捕获所有 WebSocket 接口异常
     */
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(value = "/queue/error", broadcast = false)
    public Result<?> handleWebSocketBusinessException(BusinessException e, Message<?> message) {
        // 获取 STOMP 消息头访问器
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);

        // 获取【客户端请求的路径】（等价于 request.getRequestURI()）
        String destination = headers.getDestination();

        // 获取 sessionId
        String sessionId = headers.getSessionId();

        // 打印日志
        log.error("WebSocket请求路径：{}，sessionId：{}，业务异常：{}，异常信息：{}",
                destination,
                sessionId,
                e.getMessage(),
                getTruncatedStackTrace(e)
        );
        return Result.error(e.getCode(), e.getMsg());
    }

    /**
     * WebSocket兜底异常处理器
     */
    @MessageExceptionHandler(Throwable.class)
    @SendToUser(value = "/queue/error", broadcast = false)
    public Result<?> handleWebSocketException(Throwable e, Message<?> message) {
        // 获取 STOMP 消息头访问器
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);

        // 获取【客户端请求的路径】（等价于 request.getRequestURI()）
        String destination = headers.getDestination();

        // 获取 sessionId
        String sessionId = headers.getSessionId();

        // 打印日志
        log.error("WebSocket兜底异常处理器，请求路径：{}，sessionId：{}，业务异常：{}，异常信息：{}",
                destination,
                sessionId,
                e.getMessage(),
                getTruncatedStackTrace(e)
        );

        return Result.error(SystemExceptionEnum.SYSTEM_ERROR);
    }

    // ======================== 工具方法 ========================

    private String getTruncatedStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);

        String fullStackTrace = sw.toString();
        String[] lines = fullStackTrace.split("\n");

        if (lines.length <= MAX_STACK_TRACE_LINES) {
            return fullStackTrace;
        }

        String[] truncatedLines = Arrays.copyOfRange(lines, 0, MAX_STACK_TRACE_LINES);
        String truncated = String.join("\n", truncatedLines);
        return truncated + "\n... [堆栈已截断，共 " + lines.length + " 行，仅显示前 " + MAX_STACK_TRACE_LINES + " 行]";
    }

}