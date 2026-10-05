package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.scale.AdminScaleDimensionDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleDimensionVO;

import java.util.List;

/**
 * 管理员量表维度服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleDimensionService {

    /**
     * 查询版本维度列表（按 sort 排序）
     *
     * @param scaleVersionId 量表版本ID
     * @return 维度列表
     */
    List<AdminScaleDimensionVO> listDimensions(Long scaleVersionId);

    /**
     * 新增维度
     *
     * @param dto 维度DTO {@link AdminScaleDimensionDTO}
     */
    void createDimension(AdminScaleDimensionDTO dto);

    /**
     * 修改维度
     *
     * @param dimensionId 维度ID
     * @param dto         维度DTO {@link AdminScaleDimensionDTO}
     */
    void updateDimension(Long dimensionId, AdminScaleDimensionDTO dto);

    /**
     * 调整维度排序
     *
     * @param dimensionId 维度ID
     * @param sort        排序值
     */
    void updateDimensionSort(Long dimensionId, Integer sort);

    /**
     * 逻辑删除维度（关联未删除题目时禁止删除）
     *
     * @param dimensionId 维度ID
     */
    void deleteDimension(Long dimensionId);
}