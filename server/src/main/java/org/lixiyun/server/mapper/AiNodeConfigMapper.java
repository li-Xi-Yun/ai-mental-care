package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.vo.admin.config.AiNodeGroupVO;

import java.util.List;

/**
 * AI诊断节点配置表(AiNodeConfig)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-09-07
 */
public interface AiNodeConfigMapper extends BaseMapper<AiNodeConfig> {

    /**
     * 按节点分组统计数量（用于前端分组筛选下拉）
     *
     * @return 分组及数量列表，按分组名称升序
     */
    @Select("SELECT node_group AS nodeGroup, COUNT(*) AS count FROM ai_mental_care.ai_node_config WHERE deleted = 0 GROUP BY node_group ORDER BY node_group")
    List<AiNodeGroupVO> selectNodeGroups();

    /**
     * 不过滤逻辑删除，统计指定节点名称的记录数（含已删除行）
     * <p>
     * 数据库 node_name 为单列唯一索引且覆盖软删行，而 {@code @TableLogic} 会让 wrapper 自动追加 deleted=0，
     * 无法感知软删行对唯一键的占用；创建节点前的名称预检必须使用本方法，
     * 否则重复名插入直接抛 DuplicateKeyException（500/10000）。惯例同 ScaleVersionMapper / InfraFileMapper。
     *
     * @param nodeName 节点名称
     * @return 记录数（含已删除）
     */
    @Select("SELECT COUNT(*) FROM ai_mental_care.ai_node_config WHERE node_name = #{nodeName}")
    Long countByNodeNameIncludingDeleted(@Param("nodeName") String nodeName);

    /**
     * 不过滤逻辑删除，统计指定节点名称的记录数（排除指定 id，用于更新改名预检）
     *
     * @param nodeName 节点名称
     * @param id       排除的记录ID（当前更新的节点）
     * @return 记录数（含已删除）
     */
    @Select("SELECT COUNT(*) FROM ai_mental_care.ai_node_config WHERE node_name = #{nodeName} AND id <> #{id}")
    Long countByNodeNameIncludingDeletedExcludeId(@Param("nodeName") String nodeName, @Param("id") Long id);
}