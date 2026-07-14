package org.lixiyun.pojo.entity.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库切片元数据表(KnowledgeDocument)实体类
 *
 * @author lixiyun
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_document")
public class KnowledgeDocument implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 关联文件表infra_file.id
     */
    private Long fileId;
    
    /**
     * 切片唯一ID（与Milvus主键一一对应）
     */
    private Long sliceId;
    
    /**
     * 切片原文内容
     */
    private String content;
    
    /**
     * 第一次初始分片的位置索引
     */
    private Integer chunkLevel1Idx;
    
    /**
     * 第二次分片的位置索引
     */
    private Integer chunkLevel2Idx;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

}