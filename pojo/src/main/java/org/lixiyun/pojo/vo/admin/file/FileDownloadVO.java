package org.lixiyun.pojo.vo.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 文件下载VO
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Data
@Builder
@Schema(description = "文件下载VO")
public class FileDownloadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "文件原始名称（含后缀）")
    private String originalName;

    @Schema(description = "文件字节内容")
    private byte[] bytes;
}