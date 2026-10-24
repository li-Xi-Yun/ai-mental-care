package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lixiyun.pojo.entity.knowledge.KnowledgeDocument;

/**
 * @author lixiyun
 * @since 2026-07-14 14:25
 */
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /**
     * 统计文件的一级分块总数（COUNT(DISTINCT chunk_level1_idx)）。
     *
     * @param fileId 文件ID
     * @return 一级分块数，无记录时为 0
     */
    @Select("SELECT COUNT(DISTINCT chunk_level1_idx) FROM knowledge_document WHERE file_id = #{fileId}")
    Long countDistinctLevel1(@Param("fileId") Long fileId);
}
