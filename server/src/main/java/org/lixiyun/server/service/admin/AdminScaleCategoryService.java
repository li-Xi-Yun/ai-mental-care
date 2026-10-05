package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleCategoryDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleCategoryQueryDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleCategoryVO;

/**
 * 管理员量表类别服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleCategoryService {

    /**
     * 分页查询量表类别
     *
     * @param queryDTO 查询条件 {@link AdminScaleCategoryQueryDTO}
     * @return 类别分页结果
     */
    PageResult<AdminScaleCategoryVO> pageCategory(AdminScaleCategoryQueryDTO queryDTO);

    /**
     * 获取量表类别详情
     *
     * @param id 类别ID
     * @return 类别详情
     */
    AdminScaleCategoryVO getCategoryDetail(Long id);

    /**
     * 新增量表类别
     *
     * @param dto 类别DTO {@link AdminScaleCategoryDTO}
     */
    void createCategory(AdminScaleCategoryDTO dto);

    /**
     * 修改量表类别
     *
     * @param id  类别ID
     * @param dto 类别DTO {@link AdminScaleCategoryDTO}
     */
    void updateCategory(Long id, AdminScaleCategoryDTO dto);

    /**
     * 调整量表类别排序
     *
     * @param id   类别ID
     * @param sort 排序值
     */
    void updateCategorySort(Long id, Integer sort);

    /**
     * 逻辑删除量表类别（该类别下存在未删除量表时禁止删除）
     *
     * @param id 类别ID
     */
    void deleteCategory(Long id);

    /**
     * 恢复已删除的量表类别
     *
     * @param id 类别ID
     */
    void restoreCategory(Long id);
}