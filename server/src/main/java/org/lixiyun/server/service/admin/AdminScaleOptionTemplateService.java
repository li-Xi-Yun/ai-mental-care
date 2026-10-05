package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateApplyDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateCopyDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleOptionTemplateVO;

import java.util.List;

/**
 * 管理员量表选项模板服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleOptionTemplateService {

    /**
     * 查询版本选项模板组列表（含明细）
     *
     * @param scaleVersionId 量表版本ID
     * @return 模板组列表
     */
    List<AdminScaleOptionTemplateVO> listTemplates(Long scaleVersionId);

    /**
     * 新增模板组
     *
     * @param dto 模板组DTO {@link AdminScaleOptionTemplateDTO}
     */
    void createTemplate(AdminScaleOptionTemplateDTO dto);

    /**
     * 修改模板组及明细
     *
     * @param groupId 模板组ID
     * @param dto     模板组DTO {@link AdminScaleOptionTemplateDTO}
     */
    void updateTemplate(Long groupId, AdminScaleOptionTemplateDTO dto);

    /**
     * 将模板应用到题目（把模板明细复制为该题选项，支持覆盖/追加策略）
     *
     * @param groupId 模板组ID
     * @param dto     应用DTO {@link AdminScaleOptionTemplateApplyDTO}
     */
    void applyTemplate(Long groupId, AdminScaleOptionTemplateApplyDTO dto);

    /**
     * 复制模板到目标版本
     *
     * @param dto 复制DTO {@link AdminScaleOptionTemplateCopyDTO}
     */
    void copyTemplate(AdminScaleOptionTemplateCopyDTO dto);

    /**
     * 删除模板组
     *
     * @param groupId 模板组ID
     */
    void deleteTemplate(Long groupId);
}