package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.file.FileCategoryAddDTO;
import org.lixiyun.pojo.dto.admin.file.FileCategoryUpdateDTO;
import org.lixiyun.pojo.vo.admin.file.FileCategoryVO;

import java.util.List;

/**
 * 文件分类服务接口
 *
 * @author lixiyun
 * @since 2026-05-01
 */
public interface AdminFileCategoryService {

    /**
     * 新增文件分类
     *
     * @param addDTO 新增DTO
     * @return 分类数据
     */
    FileCategoryVO addCategory(FileCategoryAddDTO addDTO);

    /**
     * 修改文件分类
     *
     * @param updateDTO 修改DTO
     * @return 分类数据
     */
    FileCategoryVO updateCategory(FileCategoryUpdateDTO updateDTO);

    /**
     * 删除文件分类（包含子分类）
     *
     * @param categoryId 分类ID
     */
    void deleteCategory(Long categoryId);

    /**
     * 查询子分类列表（树形结构）
     *
     * @param categoryId 分类ID（可选，为空则查询所有一级分类）
     * @return 子分类列表
     */
    List<FileCategoryVO> listSubCategories(Long categoryId);

    /**
     * 查询所有分类（列表结构）
     *
     * @return 分类列表
     */
    List<FileCategoryVO> listAllCategories();

    /**
     * 查询分类详情
     *
     * @param categoryId 分类ID
     * @return 分类数据
     */
    FileCategoryVO getCategoryDetail(Long categoryId);

}
