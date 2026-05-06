package org.lixiyun.pojo.vo.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件信息VO
 *
 * @author lixiyun
 * @since 2026-05-01
 */
@Data
@Schema(description = "文件信息VO")
public class FileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "文件ID")
    private Long id;

    @Schema(description = "人员ID")
    private Long personId;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "配置编号")
    private Long configId;

    @Schema(description = "文件原始名称（含后缀）")
    private String originalName;

    @Schema(description = "文件存储路径")
    private String fileUrl;

    @Schema(description = "文件后缀")
    private String fileSuffix;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "文件MD5哈希值")
    private String fileMd5;

    @Schema(description = "文件状态：0-待解析，1-解析中，2-解析失败，3-解析完成")
    private Integer status;

    @Schema(description = "解析失败原因")
    private String failReason;

    @Schema(description = "上传时间")
    private LocalDateTime createdTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updatedTime;

}
