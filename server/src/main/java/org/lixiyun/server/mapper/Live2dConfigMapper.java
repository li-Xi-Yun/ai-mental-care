package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lixiyun.pojo.entity.config.Live2dConfig;

/**
 * Live2D模型配置表(Live2dConfig)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-10-11
 */
public interface Live2dConfigMapper extends BaseMapper<Live2dConfig> {

    /**
     * 不过滤逻辑删除，统计指定模型名称的记录数（含已删除行）
     * <p>
     * 数据库 model_name 为单列唯一索引且覆盖软删行，而 {@code @TableLogic} 会让 wrapper 自动追加 deleted=0，
     * 无法感知软删行对唯一键的占用；创建记录前的名称预检必须使用本方法，
     * 否则重复名插入直接抛 DuplicateKeyException。
     *
     * @param modelName 模型名称
     * @return 记录数（含已删除）
     */
    @Select("SELECT COUNT(*) FROM ai_mental_care.live2d_config WHERE model_name = #{modelName}")
    Long countByModelNameIncludingDeleted(@Param("modelName") String modelName);

    /**
     * 不过滤逻辑删除，统计指定模型名称的记录数（排除指定 id，用于更新改名预检）
     *
     * @param modelName 模型名称
     * @param id        排除的记录ID（当前更新的记录）
     * @return 记录数（含已删除）
     */
    @Select("SELECT COUNT(*) FROM ai_mental_care.live2d_config WHERE model_name = #{modelName} AND id <> #{id}")
    Long countByModelNameIncludingDeletedExcludeId(@Param("modelName") String modelName, @Param("id") Long id);
}