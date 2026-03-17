package org.lixiyun.common.sql.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.exception.MyBatisException;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.core.utils.StringUtils;
import org.lixiyun.common.sql.enums.MyBatisExceptionEnum;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mybatis异常处理器
 *
 * @author lixiyun
 */
@Slf4j
@RestControllerAdvice
public class MybatisExceptionHandler {

    /**
     * 主键或UNIQUE索引，数据重复异常
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',数据库中已存在记录'{}'", requestURI, e.getMessage());
        return Result.error(MyBatisExceptionEnum.SQL_ERROR.getCode(), "数据库中已存在该记录，请联系管理员确认");
    }

    /**
     * Mybatis系统异常 通用处理
     */
    @ExceptionHandler(MyBatisSystemException.class)
    public Result<Void> handleCannotFindDataSourceException(MyBatisSystemException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String message = e.getMessage();
        if (StringUtils.contains("CannotFindDataSourceException", message)) {
            log.error("请求地址'{}', 未找到数据源", requestURI);
            return Result.error(MyBatisExceptionEnum.SQL_ERROR.getCode(), "未找到数据源，请联系管理员确认");
        }
        log.error("请求地址'{}', Mybatis系统异常", requestURI, e);
        return Result.error(MyBatisExceptionEnum.SQL_ERROR.getCode(), message);
    }

    /**
     * 自定义MyBatis异常
     */
    @ExceptionHandler(MyBatisException.class)
    public Result<Void> handleMyBatisSystemException(MyBatisException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}', Mybatis系统异常", requestURI, e);
        return Result.error(e.getCode(), e.getMsg());
    }


}
