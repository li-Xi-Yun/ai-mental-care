package org.lixiyun.server.service;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.scale.ScaleDTO;
import org.lixiyun.pojo.vo.scale.ScaleVO;

/**
 * @author lixiyun
 * @since 2026-04-15 09:22
 */
public interface ScaleService {

    /**
     * 创建一个新的量表。
     *
     * @param scaleDTO 包含要创建的量表信息的DTO
     */
    void createScale(ScaleDTO scaleDTO);

    /**
     * 检索量表的分页列表，可选择按类别过滤。
     *
     * @param pageNum         要检索的页码
     * @param pageSize        每页的项目数
     * @param scaleCategoryId 要按其过滤的量表类别的ID（可选）
     * @return 包含ScaleVO对象列表的PageResult
     */
    PageResult<ScaleVO> listScales(Integer pageNum, Integer pageSize, Long scaleCategoryId);

    /**
     * 根据ID删除量表。
     *
     * @param scaleId 要删除的量表的ID
     */
    void deleteScale(Long scaleId);

    /**
     * 更新现有的量表。
     *
     * @param scaleId      要更新的量表的ID
     * @param dto     包含更新的量表信息的DTO
     */
    void updateScale(Long scaleId, ScaleDTO dto);
}
