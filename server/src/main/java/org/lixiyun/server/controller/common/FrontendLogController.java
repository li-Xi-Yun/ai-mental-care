package org.lixiyun.server.controller.common;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.dto.common.FrontendLogEntryDTO;
import org.lixiyun.pojo.dto.common.FrontendLogUploadRequestDTO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前端日志收集控制器
 * <p>接收前端页面上报的日志，通过 logback 中 name="frontend" 的 logger
 * 将日志写入独立的 frontend 子目录，与后端日志隔离存储</p>
 *
 * @author lixiyun
 * @since 2026-09-27
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/common/logs/frontend")
public class FrontendLogController {

    @PostMapping("/upload")
    @Operation(summary = "前端日志批量上报", description = "前端日志批量上报")
    public Result<Void> upload(@RequestBody @Validated FrontendLogUploadRequestDTO request) {
        for (FrontendLogEntryDTO entry : request.getLogs()) {
            String formattedMsg = String.format("[%s] %s", entry.getModule(), entry.getMessage());
            switch (entry.getLevel()) {
                case "DEBUG" -> log.debug(formattedMsg);
                case "INFO"  -> log.info(formattedMsg);
                case "WARN"  -> log.warn(formattedMsg);
                case "ERROR" -> log.error(formattedMsg, entry.getStack());
                default      -> log.info(formattedMsg);
            }
        }
        return Result.success();
    }

}