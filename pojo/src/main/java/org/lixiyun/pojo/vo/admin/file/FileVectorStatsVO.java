package org.lixiyun.pojo.vo.admin.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文件向量分块统计VO
 *
 * @author lixiyun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件向量分块统计VO")
public class FileVectorStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 二级分块总数（=knowledge_document行数=向量片段总数，解析中随批次增长） */
    @Schema(description = "二级分块总数")
    private Long level2Count;

    /** 一级分块总数（COUNT(DISTINCT chunk_level1_idx)，全量精确值） */
    @Schema(description = "一级分块总数")
    private Long level1Count;

}
