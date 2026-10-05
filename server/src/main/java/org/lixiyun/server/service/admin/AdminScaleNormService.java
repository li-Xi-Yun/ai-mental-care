package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormDeleteDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleNormGroupDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleNormGroupVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleNormVO;

import java.util.List;

/**
 * 管理员量表常模服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleNormService {

    /**
     * 查询版本常模组列表（含人口学筛选条件、mean/sd、source 等）
     *
     * @param scaleVersionId 量表版本ID
     * @return 常模组列表
     */
    List<AdminScaleNormGroupVO> listNormGroups(Long scaleVersionId);

    /**
     * 获取常模组详情（含其常模明细）
     *
     * @param groupId 常模组ID
     * @return 常模组详情
     */
    AdminScaleNormGroupVO getNormGroupDetail(Long groupId);

    /**
     * 新增常模组
     *
     * @param dto 常模组DTO {@link AdminScaleNormGroupDTO}
     */
    void createNormGroup(AdminScaleNormGroupDTO dto);

    /**
     * 修改常模组（含切换 normType：0 公式法只需 mean/sd；1 查表法要求有明细）
     *
     * @param groupId 常模组ID
     * @param dto     常模组DTO {@link AdminScaleNormGroupDTO}
     */
    void updateNormGroup(Long groupId, AdminScaleNormGroupDTO dto);

    /**
     * 逻辑删除常模组（级联逻辑删除其常模明细；被测评记录引用时禁止删除）
     *
     * @param groupId 常模组ID
     */
    void deleteNormGroup(Long groupId);

    /**
     * 查询常模组下常模明细全量列表
     *
     * @param normGroupId 常模组ID
     * @return 常模明细列表
     */
    List<AdminScaleNormVO> listNorms(Long normGroupId);

    /**
     * 批量新增/更新常模明细（按唯一键 upsert）
     *
     * @param dto 批量保存DTO {@link AdminScaleNormBatchDTO}
     */
    void batchSaveNorms(AdminScaleNormBatchDTO dto);

    /**
     * 修改单条常模明细
     *
     * @param normId 常模明细ID
     * @param dto    常模明细DTO {@link AdminScaleNormDTO}
     */
    void updateNorm(Long normId, AdminScaleNormDTO dto);

    /**
     * 批量删除常模明细
     *
     * @param dto 批量删除DTO {@link AdminScaleNormDeleteDTO}
     */
    void batchDeleteNorms(AdminScaleNormDeleteDTO dto);
}