package org.lixiyun.pojo.entity.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 专用于Milvus数据库的字段信息
 * @author lixiyun
 * @since 2026-05-03 12:28
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VectorData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 自增主键ID
     */
    private Long id;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 一级分块索引
     */
    private Integer chunkLevel1Idx;

    /**
     * 二级分块索引
     */
    private Integer chunkLevel2Idx;

    /**
     * 是否删除，0-否，1-是
     */
    private Integer deleted;

    /**
     * 文本内容
     */
    private String content;

    /**
     * 向量数据
     */
    private float[] vector;

}