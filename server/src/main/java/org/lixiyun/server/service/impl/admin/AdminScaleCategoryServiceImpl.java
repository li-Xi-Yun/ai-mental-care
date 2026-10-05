package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleCategoryDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleCategoryQueryDTO;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleCategory;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleCategoryVO;
import org.lixiyun.server.mapper.ScaleCategoryMapper;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.service.admin.AdminScaleCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理员量表类别服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleCategoryServiceImpl implements AdminScaleCategoryService {

    private final ScaleCategoryMapper scaleCategoryMapper;
    private final ScaleMapper scaleMapper;
    private final ScaleAuditNameHelper scaleAuditNameHelper;

    @Override
    public PageResult<AdminScaleCategoryVO> pageCategory(AdminScaleCategoryQueryDTO queryDTO) {
        log.info("量表类别分页查询，参数：{}", queryDTO);
        String categoryName = queryDTO.getCategoryName();
        Integer deletedFlag = queryDTO.getDeletedFlag();
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();

        Page<AdminScaleCategoryVO> page = scaleCategoryMapper.pageCategories(new Page<>(pageNum, pageSize), categoryName, deletedFlag);
        fillAuditNames(page.getRecords());
        log.debug("量表类别分页查询完成，总数：{}", page.getTotal());
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    @Override
    public AdminScaleCategoryVO getCategoryDetail(Long id) {
        log.info("量表类别详情查询，参数：{}", id);
        ScaleCategory category = scaleCategoryMapper.selectById(id);
        if (category == null) {
            log.warn("量表类别不存在，类别ID：{}", id);
            throw new BusinessException(ScaleExceptionEnum.SCALE_CATEGORY_NOT_FOUND);
        }
        AdminScaleCategoryVO vo = BeanUtil.copyProperties(category, AdminScaleCategoryVO.class);
        fillAuditName(vo, category);
        return vo;
    }

    @Override
    public void createCategory(AdminScaleCategoryDTO dto) {
        log.info("新增量表类别，参数：{}", dto.getCategoryName());
        String categoryName = dto.getCategoryName();
        Long count = scaleCategoryMapper.selectCount(new LambdaQueryWrapper<ScaleCategory>()
                .eq(ScaleCategory::getCategoryName, categoryName));
        if (count > 0) {
            log.warn("量表类别名称已存在：{}", categoryName);
            throw new BusinessException(ScaleExceptionEnum.SCALE_CATEGORY_NAME_EXISTS);
        }
        ScaleCategory category = BeanUtil.copyProperties(dto, ScaleCategory.class);
        int row = scaleCategoryMapper.insert(category);
        if (row == 0) {
            log.error("量表类别新增失败：{}", categoryName);
            throw new BusinessException(ScaleExceptionEnum.SCALE_CATEGORY_ADD_FAIL);
        }
        log.info("量表类别新增成功，类别ID：{}", category.getId());
    }

    @Override
    public void updateCategory(Long id, AdminScaleCategoryDTO dto) {
        log.info("修改量表类别，类别ID：{}，参数：{}", id, dto.getCategoryName());
        ScaleCategory category = BeanUtil.copyProperties(dto, ScaleCategory.class);
        category.setId(id);
        int row = scaleCategoryMapper.updateById(category);
        if (row == 0) {
            log.warn("量表类别不存在，类别ID：{}", id);
            throw new BusinessException(ScaleExceptionEnum.SCALE_CATEGORY_NOT_FOUND);
        }
        log.info("量表类别修改成功，类别ID：{}", id);
    }

    @Override
    public void updateCategorySort(Long id, Integer sort) {
        log.info("调整量表类别排序，类别ID：{}，排序：{}", id, sort);
        scaleCategoryMapper.update(null, new LambdaUpdateWrapper<ScaleCategory>()
                .eq(ScaleCategory::getId, id)
                .set(ScaleCategory::getSort, sort));
    }

    @Override
    public void deleteCategory(Long id) {
        log.info("删除量表类别，类别ID：{}", id);
        Long scaleCount = scaleMapper.selectCount(new LambdaQueryWrapper<Scale>()
                .eq(Scale::getScaleCategoryId, id));
        if (scaleCount > 0) {
            log.warn("量表类别下存在量表，无法删除，类别ID：{}", id);
            throw new BusinessException(ScaleExceptionEnum.SCALE_CATEGORY_IN_USE);
        }
        int row = scaleCategoryMapper.deleteById(id);
        if (row == 0) {
            log.error("量表类别删除失败，类别ID：{}", id);
            throw new BusinessException(ScaleExceptionEnum.SCALE_CATEGORY_DELETE_FAIL);
        }
        log.info("量表类别删除成功，类别ID：{}", id);
    }

    @Override
    public void restoreCategory(Long id) {
        log.info("恢复量表类别，类别ID：{}", id);
        int row = scaleCategoryMapper.restoreCategory(id);
        if (row == 0) {
            log.warn("量表类别不存在，类别ID：{}", id);
            throw new BusinessException(ScaleExceptionEnum.SCALE_CATEGORY_NOT_FOUND);
        }
        log.info("量表类别恢复成功，类别ID：{}", id);
    }

    /**
     * 批量填充类别VO的创建人/更新人姓名
     *
     * @param records 类别VO列表
     */
    private void fillAuditNames(List<AdminScaleCategoryVO> records) {
        if (CollUtil.isEmpty(records)) {
            return;
        }
        List<Long> categoryIds = StreamUtils.toList(records, AdminScaleCategoryVO::getId);
        if (CollUtil.isEmpty(categoryIds)) {
            return;
        }
        List<ScaleCategory> categories = scaleCategoryMapper.selectBatchIds(categoryIds);
        if (CollUtil.isEmpty(categories)) {
            return;
        }
        Map<Long, ScaleCategory> categoryMap = StreamUtils.toIdentityMap(categories, ScaleCategory::getId);
        List<Long> personIds = new ArrayList<>();
        categories.forEach(category -> {
            if (category.getCreatedBy() != null) {
                personIds.add(category.getCreatedBy());
            }
            if (category.getUpdatedBy() != null) {
                personIds.add(category.getUpdatedBy());
            }
        });
        Map<Long, String> nameMap = scaleAuditNameHelper.resolveNames(personIds);
        records.forEach(vo -> {
            ScaleCategory category = categoryMap.get(vo.getId());
            if (category != null) {
                if (category.getCreatedBy() != null) {
                    vo.setCreatedByName(nameMap.get(category.getCreatedBy()));
                }
                if (category.getUpdatedBy() != null) {
                    vo.setUpdatedByName(nameMap.get(category.getUpdatedBy()));
                }
            }
        });
    }

    /**
     * 填充单个类别VO的创建人/更新人姓名
     *
     * @param vo       类别VO
     * @param category 类别实体
     */
    private void fillAuditName(AdminScaleCategoryVO vo, ScaleCategory category) {
        List<Long> personIds = new ArrayList<>();
        if (category.getCreatedBy() != null) {
            personIds.add(category.getCreatedBy());
        }
        if (category.getUpdatedBy() != null) {
            personIds.add(category.getUpdatedBy());
        }
        if (CollUtil.isEmpty(personIds)) {
            return;
        }
        Map<Long, String> nameMap = scaleAuditNameHelper.resolveNames(personIds);
        if (category.getCreatedBy() != null) {
            vo.setCreatedByName(nameMap.get(category.getCreatedBy()));
        }
        if (category.getUpdatedBy() != null) {
            vo.setUpdatedByName(nameMap.get(category.getUpdatedBy()));
        }
    }
}
