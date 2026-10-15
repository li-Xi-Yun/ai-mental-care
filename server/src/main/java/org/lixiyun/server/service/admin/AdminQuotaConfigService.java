package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.quota.QuotaConfigUpdateDTO;
import org.lixiyun.pojo.vo.admin.quota.QuotaConfigVO;

import java.util.List;

/**
 * 管理员 AI 对话配额全局配置服务接口
 * <p>承载 quota_config 全局默认配置的查询与局部更新，供 {@code AdminQuotaController} 调用。</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
public interface AdminQuotaConfigService {

    /**
     * 查询全部资源类型的全局配置
     * <p>返回各资源类型默认上限、可用性状态、限流状态、周期与备注（管理员列表页用）。</p>
     *
     * @return 全局配置VO列表
     */
    List<QuotaConfigVO> listConfig();

    /**
     * 修改单个资源的全局默认配置
     * <p>局部更新，未传字段保持原值；成功后刷新本地缓存。</p>
     *
     * @param resourceType 资源类型 {@link org.lixiyun.pojo.entity.quota.QuotaConfig}
     * @param dto          更新参数 {@link QuotaConfigUpdateDTO}
     */
    void updateConfig(Integer resourceType, QuotaConfigUpdateDTO dto);
}