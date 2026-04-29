package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.scale.ScaleOptionTemplateDTO;
import org.lixiyun.pojo.vo.scale.ScaleOptionTemplateVO;

import java.util.List;

/**
 * 管理员量表选项模板服务接口
 *
 * @author lixiyun
 * @since 2026-04-20 21:39
 */
public interface AdminScaleOptionTemplateService {

    /**
     * 创建选项模板
     * <p>
     * 批量创建量表选项模板，支持一次创建多个选项模板
     * </p>
     *
     * @param dtoList 选项模板DTO列表 {@link ScaleOptionTemplateDTO}
     */
    void createTemplate(List<ScaleOptionTemplateDTO> dtoList);

    /**
     * 查询选项模板列表
     * <p>
     * 根据量表ID查询该量表下的所有选项模板列表
     * </p>
     *
     * @param scaleId 量表ID
     * @return 选项模板VO列表 {@link ScaleOptionTemplateVO}
     */
    List<ScaleOptionTemplateVO> listTemplates(Long scaleId);

    /**
     * 更新选项模板
     * <p>
     * 批量更新量表选项模板，支持一次更新多个选项模板
     * </p>
     *
     * @param dtoList 选项模板DTO列表 {@link ScaleOptionTemplateDTO}
     */
    void updateTemplate(List<ScaleOptionTemplateDTO> dtoList);

    /**
     * 删除选项模板
     * <p>
     * 根据模板ID逻辑删除选项模板
     * </p>
     *
     * @param templateId 选项模板ID
     */
    void deleteTemplate(Long templateId);
}
