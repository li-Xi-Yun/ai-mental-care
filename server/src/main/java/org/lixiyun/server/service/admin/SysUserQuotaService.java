package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.quota.QuotaUserAdjustDTO;
import org.lixiyun.pojo.vo.admin.quota.QuotaUserVO;

import java.util.List;

/**
 * 管理员 AI 对话配额用户调整服务接口
 * <p>承载 quota_user 用户级配额的查询与管理调整，供 {@code SysUserQuotaController} 调用。</p>
 *
 * @author lixiyun
 * @since 2026-10-15
 */
public interface SysUserQuotaService {

    /**
     * 查询单个用户全部资源类型的配额（调整弹窗加载用，按全局配置基底合并用户行）
     *
     * @param userId 用户ID
     * @return 用户全部资源配额VO列表
     */
    List<QuotaUserVO> listUserQuota(Long userId);

    /**
     * 调整指定用户某资源的个性化上限 + 已用次数（管理员调整，落审计明细）
     *
     * @param userId       用户ID
     * @param resourceType 资源类型 {@link org.lixiyun.pojo.entity.quota.QuotaConfig}
     * @param dto          调整参数 {@link QuotaUserAdjustDTO}
     */
    void updateUserQuota(Long userId, Integer resourceType, QuotaUserAdjustDTO dto);
}