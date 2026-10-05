package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
    @Select("SELECT node_group AS nodeGroup, COUNT(*) AS count FROM ai_node_config WHERE deleted = 0 GROUP BY node_group ORDER BY node_group")
    List<AiNodeGroupVO> selectNodeGroups();
}