package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.user.scale.ScaleCategoryDTO;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleCategory;
import org.lixiyun.pojo.vo.user.scale.ScaleCategoryVO;
import org.lixiyun.server.mapper.ScaleCategoryMapper;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.service.admin.AdminScaleCategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-19 14:50
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleCategoryServiceImpl implements AdminScaleCategoryService {

    private final ScaleCategoryMapper scaleCategoryMapper;
    private final ScaleMapper scaleMapper;

    @Override
    public void createCategory(ScaleCategoryDTO dto) {
        ScaleCategory scaleCategory = scaleCategoryMapper.selectOne(new LambdaQueryWrapper<ScaleCategory>()
                .eq(ScaleCategory::getCategoryName, dto.getCategoryName())
                .eq(ScaleCategory::getDeleted, DeleteConstant.DELETE_FLAG_YES));
        if (scaleCategory != null) {
            scaleCategoryMapper.update(new ScaleCategory(), new LambdaQueryWrapper<ScaleCategory>()
                    .eq(ScaleCategory::getId, scaleCategory.getId())
                    .eq(ScaleCategory::getDeleted, DeleteConstant.DELETE_FLAG_NO));
            log.info("量表类别已存在，ID: {}", scaleCategory.getId());
            return;
        }

        ScaleCategory category = new ScaleCategory();
        BeanUtil.copyProperties(dto, category);
        scaleCategoryMapper.insert(category);
        log.info("创建量表类别成功，ID: {}", category.getId());
    }

    @Override
    public void deleteCategory(Long categoryId) {
        // 检查是否有量表使用
        Long count = scaleMapper.selectCount(new LambdaQueryWrapper<Scale>()
                .eq(Scale::getScaleCategoryId, categoryId)
                .eq(Scale::getDeleted, org.lixiyun.pojo.constant.DeleteConstant.DELETE_FLAG_NO));
        if (count > 0) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_CATEGORY_IN_USE);
        }
        scaleCategoryMapper.deleteById(categoryId);
        log.info("删除量表类别成功，categoryId: {}", categoryId);
    }

    @Override
    public void updateCategory(Long categoryId, ScaleCategoryDTO dto) {
        ScaleCategory category = new ScaleCategory();
        BeanUtil.copyProperties(dto, category);
        category.setId(categoryId);
        scaleCategoryMapper.updateById(category);
        log.info("修改量表类别成功，categoryId: {}", categoryId);
    }

    @Override
    public List<ScaleCategoryVO> listCategories() {
        List<ScaleCategory> categories = scaleCategoryMapper.selectList(null);
        return StreamUtils.toListVO(categories, ScaleCategoryVO.class);
    }
}
