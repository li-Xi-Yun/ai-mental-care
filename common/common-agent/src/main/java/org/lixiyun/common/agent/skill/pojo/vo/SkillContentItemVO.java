package org.lixiyun.common.agent.skill.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Skill文件夹内容项VO
 * <p>用于表示文件夹中的文件或子文件夹信息</p>
 *
 * @author lixiyun
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Skill文件夹内容项")
public class SkillContentItemVO implements Serializable {

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

    /**
     * 相对路径（相对于skill文件夹的根目录）
     */
    @Schema(description = "相对于skill文件夹的根目录的相对路径", example = "subfolder/example.txt")
    private String relativePath;

}
