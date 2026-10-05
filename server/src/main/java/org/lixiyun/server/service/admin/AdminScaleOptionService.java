package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionBatchDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionDeleteDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionSortItemDTO;

import java.util.List;

/**
 * 管理员量表选项服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleOptionService {

    /**
     * 为题目批量新增选项
     *
     * @param dto 批量新增DTO {@link AdminScaleOptionBatchDTO}
     */
    void batchAddOptions(AdminScaleOptionBatchDTO dto);

    /**
     * 修改单个选项
     *
     * @param optionId 选项ID
     * @param dto      选项DTO {@link AdminScaleOptionDTO}
     */
    void updateOption(Long optionId, AdminScaleOptionDTO dto);

    /**
     * 批量调整选项顺序
     *
     * @param sortItems 排序项列表 {@link AdminScaleOptionSortItemDTO}
     */
    void updateOptionSort(List<AdminScaleOptionSortItemDTO> sortItems);

    /**
     * 批量删除选项（需保证选项归属题目一致）
     *
     * @param dto 批量删除DTO {@link AdminScaleOptionDeleteDTO}
     */
    void batchDeleteOptions(AdminScaleOptionDeleteDTO dto);
}