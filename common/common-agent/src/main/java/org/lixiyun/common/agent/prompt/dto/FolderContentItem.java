package org.lixiyun.common.agent.prompt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文件夹内容项DTO
 * <p>用于表示文件夹中的文件或子文件夹信息</p>
 *
 * @author lixiyun
 * @since 2026-04-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件夹内容项")
public class FolderContentItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 名称（文件名或文件夹名）
     */
    @Schema(description = "名称", example = "example.txt")
    private String name;

    /**
     * 类型：0-文件，1-文件夹
     */
    @Schema(description = "类型：0-文件，1-文件夹", example = "0", allowableValues = {"0", "1"})
    private Integer type;
}
