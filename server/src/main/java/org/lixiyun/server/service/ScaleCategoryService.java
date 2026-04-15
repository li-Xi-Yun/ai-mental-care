package org.lixiyun.server.service;

import org.lixiyun.pojo.dto.scale.ScaleCategoryDTO;
import org.lixiyun.pojo.vo.scale.ScaleCategoryVO;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 14:22
 */
public interface ScaleCategoryService {

    /**
     * 创建一个新的量表分类。
     *
     * @param dto 包含要创建的分类信息的DTO
     */
    void createCategory(ScaleCategoryDTO dto);

    /**
     * 检索所有量表分类的列表。
     *
     * @return 表示分类的ScaleCategoryVO对象列表
     */
    List<ScaleCategoryVO> listCategories();

    /**
     * 根据ID删除量表分类。
     *
     * @param categoryId 要删除的分类的ID
     */
    void deleteCategory(Long categoryId);

    /**
     * 更新现有的量表分类。
     *
     * @param categoryId  要更新的分类的ID
     * @param dto 包含更新的分类信息的DTO
     */
    void updateCategory(Long categoryId, ScaleCategoryDTO dto);
}
