package org.lixiyun.pojo.vo.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 文件向量信息VO
 *
 * @author lixiyun
 * @since 2026-05-02
 */
@Data
@Schema(description = "文件向量信息VO")
public class FileVectorVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "向量ID")
    private Long id;

    @Schema(description = "文件ID")
    private Long fileId;

    @Schema(description = "一级分块索引")
    private Integer chunkLevel1Idx;

    @Schema(description = "二级分块索引")
    private Integer chunkLevel2Idx;

    @Schema(description = "分块内容")
    private String chunkContent;

}
