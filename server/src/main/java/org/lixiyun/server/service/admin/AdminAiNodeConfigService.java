package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.config.AdminAiNodeConfigQueryDTO;
import org.lixiyun.pojo.dto.admin.config.AiNodeConfigBatchEnabledDTO;
import org.lixiyun.pojo.dto.admin.config.AiNodeConfigDTO;
import org.lixiyun.pojo.vo.admin.config.AiNodeConfigSimpleVO;
import org.lixiyun.pojo.vo.admin.config.AiNodeConfigVO;
import org.lixiyun.pojo.vo.admin.config.AiNodeGroupVO;
import org.lixiyun.pojo.vo.admin.config.AiNodeKeyVO;

import java.util.List;

/**
 * 管理员AI节点配置服务接口
 *
 * @author lixiyun
 * @since 2026-09-07
 */
public interface AdminAiNodeConfigService {

    /**
     * 分页查询AI节点配置
     *
     * @param queryDTO 查询条件 {@link AdminAiNodeConfigQueryDTO}
     * @return 分页结果（简要信息，不含提示词全文）
     */
    PageResult<AiNodeConfigSimpleVO> pageAiNodeConfig(AdminAiNodeConfigQueryDTO queryDTO);

    /**
     * 获取AI节点配置详情
     *
     * @param id 节点配置主键ID
     * @return 节点配置详情（含提示词全文）
     */
    AiNodeConfigVO getAiNodeConfigDetail(Long id);

    /**
     * 新增AI节点配置
     *
     * @param dto 节点配置DTO {@link AiNodeConfigDTO}
     */
    void createAiNodeConfig(AiNodeConfigDTO dto);

    /**
     * 修改AI节点配置
     *
     * @param id  节点配置主键ID
     * @param dto 节点配置DTO {@link AiNodeConfigDTO}
     */
    void updateAiNodeConfig(Long id, AiNodeConfigDTO dto);

    /**
     * 删除AI节点配置
     *
     * @param id 节点配置主键ID
     */
    void deleteAiNodeConfig(Long id);

    /**
     * 批量启用/禁用AI节点配置
     *
     * @param dto 批量启用/禁用DTO {@link AiNodeConfigBatchEnabledDTO}
     */
    void batchUpdateEnabled(AiNodeConfigBatchEnabledDTO dto);

    /**
     * 获取全部节点标识列表（下拉选项用）
     * <p>返回节点ID、唯一标识、中文名称与分组，便于前端下拉展示可读文案</p>
     *
     * @return 节点标识列表
     */
    List<AiNodeKeyVO> listNodeKeys();

    /**
     * 获取节点分组列表（分组筛选下拉用）
     * <p>按节点分组统计数量，返回去重后的分组及数量</p>
     *
     * @return 节点分组列表
     */
    List<AiNodeGroupVO> listNodeGroups();
}