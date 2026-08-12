package org.lixiyun.pojo.entity.file;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.constant.DeleteConstant;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件元数据表(InfraFile)实体类
 *
 * @author lixiyun
 * @since 2026-05-01 00:00:00
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InfraFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /**文件状态：待解析 */
    public static final int STATUS_PENDING = 0;
    /** 文件状态：解析中 */
    public static final int STATUS_PARSING = 1;
    /** 文件状态：解析失败 */
    public static final int STATUS_PARSE_FAILED = 2;
    /** 文件状态：解析完成 */
    public static final int STATUS_PARSE_COMPLETED = 3;

    /** 向量状态：禁用 */
    public static final int VECTOR_STATUS_DISABLE = 0;
    /** 向量状态：启用 */
    public static final int VECTOR_STATUS_ENABLE = 1;

    /** 向量库知识类型：无 */
    public static final int KNOWLEDGE_TYPE_NONE = 0;
    /** 向量库知识类型：症状库 */
    public static final int KNOWLEDGE_TYPE_SYMPTOM = 1;
    /** 向量库知识类型：诊断标准库 */
    public static final int KNOWLEDGE_TYPE_DIAGNOSIS = 2;
    /** 向量库知识类型：干预方案库 */
    public static final int KNOWLEDGE_TYPE_INTERVENTION_PLAN = 3;

    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 人员ID，关联用户/管理员表
     */
    private Long personId;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 配置编号，默认0-本地存储
     */
    private Long configId;

    /**
     * 文件原始名称（含后缀）
     */
    private String originalName;

    /**
     * 文件存储路径（本地相对/绝对路径）
     */
    private String fileUrl;

    /**
     * 文件后缀（例：doc、pdf）
     */
    private String fileSuffix;

    /**
     * 文件大小，单位：字节
     */
    private Long fileSize;

    /**
     * 文件MD5哈希值，用于去重/校验
     */
    private String fileMd5;

    /**
     * 文件状态：0-待解析，1-解析中，2-解析失败，3-解析完成
     */
    private Integer status;

    /**
     * 解析失败原因
     */
    private String failReason;

    /**
     * 是否启用向量检索，0-否，1-是
     */
    private Integer vectorStatus;

    /**
     * 向量库知识类型，0-无，1=症状库 2=诊断标准库 3=干预方案库
     */
    private Integer knowledgeType;

    /**
     * 文件来源
     */
    private String source;

    /**
     * 上传时间
     */
    private LocalDateTime createdTime;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 是否删除，0-否，1-是
     */
    @TableLogic
    private Integer deleted;

    /**
     * 判断文件状态是否为待解析
     *
     * @return true-待解析，false-其他状态
     */
    public boolean isPending() {
        return STATUS_PENDING == this.status;
    }

    /**
     * 判断文件状态是否为解析中
     *
     * @return true-解析中，false-其他状态
     */
    public boolean isParsing() {
        return STATUS_PARSING == this.status;
    }

    /**
     * 判断文件状态是否为解析失败
     *
     * @return true-解析失败，false-其他状态
     */
    public boolean isParseFailed() {
        return STATUS_PARSE_FAILED == this.status;
    }

    /**
     * 判断文件状态是否为解析完成
     *
     * @return true-解析完成，false-其他状态
     */
    public boolean isParseCompleted() {
        return STATUS_PARSE_COMPLETED == this.status;
    }

    /**
     * 判断文件是否已删除
     *
     * @return true表示已删除，false表示未删除
     */
    public boolean deleteFlat() {
        return deleted != null && deleted == DeleteConstant.DELETE_FLAG_YES;
    }

    /**
     * 判断文件是否已启用向量检索
     *
     * @return true-已启用向量检索，false-未启用向量检索
     */
    public boolean isVectorStatusEnable() {
        return VECTOR_STATUS_ENABLE == this.vectorStatus;
    }

    /**
     * 判断文件是否已禁用向量检索
     *
     * @return true-已禁用向量检索，false-未禁用向量检索
     */
    public boolean isVectorStatusDisable() {
        return VECTOR_STATUS_DISABLE == this.vectorStatus;
    }
}