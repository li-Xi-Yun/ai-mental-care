package org.lixiyun.pojo.dto.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 文件向量解析中断DTO
 *
 * @author lixiyun
 * @since 2026-05-02
 */
@Data
@Schema(description = "文件向量解析中断DTO")
public class FileVectorInterruptDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "文件ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

}
