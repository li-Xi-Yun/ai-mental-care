package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.scale.ScaleOptionBatchAddDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleOptionDTO;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 16:27
 */
public interface AdminScaleOptionService {

    /**
     * 批量新增选项
     *
     * @param addDTO 批量新增选项DTO {@link ScaleOptionBatchAddDTO}
     */
    void batchAddOptions(ScaleOptionBatchAddDTO addDTO);

    /**
     * 删除给定问题的多个选项。
     *
     * @param questionId 问题的ID
     * @param optionIds  要删除的选项ID列表
     */
    void deleteOptions(Long questionId, List<Long> optionIds);

    /**
     * 使用提供的数据更新选项。
     *
     * @param optionId   要更新的选项的ID
     * @param updateDTO  包含更新的选项信息的DTO
     */
    void updateOption(Long optionId, ScaleOptionDTO updateDTO);
}
