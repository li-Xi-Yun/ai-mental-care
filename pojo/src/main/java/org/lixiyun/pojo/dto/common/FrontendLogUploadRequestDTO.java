package org.lixiyun.pojo.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 前端日志批量上报请求
 *
 * @author lixiyun
 * @since 2026-09-27
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "前端日志批量上报请求")
public class FrontendLogUploadRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "日志列表不能为空")
    @Schema(description = "日志条目列表")
    private List<FrontendLogEntryDTO> logs;

}