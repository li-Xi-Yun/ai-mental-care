package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.dto.admin.file.FileCategoryAddDTO;
import org.lixiyun.pojo.dto.admin.file.FileCategoryUpdateDTO;
import org.lixiyun.pojo.entity.file.InfraFileCategory;
import org.lixiyun.pojo.vo.admin.file.FileCategoryVO;
import org.lixiyun.server.mapper.InfraFileCategoryMapper;
import org.lixiyun.server.mapper.InfraFileMapper;
import org.lixiyun.server.service.admin.AdminFileCategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 文件分类服务实现类
 *
 * @author lixiyun
 * @since 2026-05-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFileCategoryServiceImpl implements AdminFileCategoryService {

    private final InfraFileCategoryMapper infraFileCategoryMapper;
    private final InfraFileMapper infraFileMapper;

    @Override
    public FileCategoryVO addCategory(FileCategoryAddDTO addDTO) {
        log.info("开始新增文件分类，分类名称：{}，父级ID：{}", addDTO.getCategoryName(), addDTO.getParentId());

        if(addDTO.getParentId() != null && addDTO.getParentId() == InfraFileCategory.DEFAULT_CATEGORY_ID){
            log.error("默认分类不允许有子分类");
            throw new BusinessException(FileExceptionEnum.FILE_CATEGORY_NOT_ALLOWED_CHILD);
        }

        // 构建实体对象
        InfraFileCategory category = BeanUtil.copyProperties(addDTO, InfraFileCategory.class);
        Long currentId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        category.setPersonId(currentId);

        // DB新增分类数据
        int inserted = infraFileCategoryMapper.insert(category);
        if (inserted == 0) {
            log.error("文件分类新增失败");
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        log.info("文件分类新增成功，分类ID：{}", category.getId());

        // 转换为VO并返回
        return BeanUtil.copyProperties(category, FileCategoryVO.class);
    }

    @Override
    public FileCategoryVO updateCategory(FileCategoryUpdateDTO updateDTO) {
        log.info("开始修改文件分类，分类ID：{}，分类名称：{}", updateDTO.getId(), updateDTO.getCategoryName());
        Long categoryId = updateDTO.getId();
        String categoryName = updateDTO.getCategoryName();

        if(categoryId == InfraFileCategory.DEFAULT_CATEGORY_ID){
            log.error("默认分类不允许修改名称");
            throw new BusinessException(FileExceptionEnum.FILE_CATEGORY_NOT_ALLOWED_UPDATE);
        }

        // 构建更新实体
        InfraFileCategory updateEntity = InfraFileCategory.builder()
                        .id(categoryId)
                        .categoryName(categoryName)
                        .build();

        // DB修改分类数据
        int updated = infraFileCategoryMapper.updateById(updateEntity);
        if (updated == 0) {
            log.error("文件分类修改失败，分类ID：{}", categoryId);
            throw new BusinessException(SystemExceptionEnum.SYSTEM_ERROR);
        }

        log.info("文件分类修改成功，分类ID：{}", updateEntity.getId());

        // DB查询更新后的分类数据
        InfraFileCategory updatedCategory = infraFileCategoryMapper.selectById(categoryId);

        // 转换为VO并返回
        return BeanUtil.copyProperties(updatedCategory, FileCategoryVO.class);
    }

    @Override
    public void deleteCategory(Long categoryId) {
        log.info("开始删除文件分类，分类ID：{}", categoryId);

        // DB查询分类是否存在
        InfraFileCategory category = infraFileCategoryMapper.selectById(categoryId);
        if (category == null) {
            log.error("文件分类不存在，分类ID：{}", categoryId);
            throw new BusinessException(FileExceptionEnum.FILE_CATEGORY_NOT_FOUND);
        }

        // 递归查询所有子分类并检查是否存在文件
        List<Long> categoryIds = getAllSubCategoryIdsAndCheckFiles(categoryId);

        // DB物理删除分类数据（包含子分类）
        int deleted = infraFileCategoryMapper.deleteBatchByIds(categoryIds);
        log.info("文件分类删除成功，删除数量：{}", deleted);
    }

    @Override
    public List<FileCategoryVO> listSubCategories(Long categoryId) {
        log.debug("开始查询子分类列表，分类ID：{}", categoryId);

        // DB查询该ID下的所有子分类信息
        List<InfraFileCategory> categories;
        // 查询所有一级分类
        // 查询指定分类的直接子分类
        categories = infraFileCategoryMapper.selectList(
                new LambdaQueryWrapper<InfraFileCategory>()
                        .eq(InfraFileCategory::getParentId, Objects.requireNonNullElse(categoryId, InfraFileCategory.FIRST_LEVEL_CATEGORY))
                        .orderByAsc(InfraFileCategory::getCategoryName)
        );

        log.debug("查询子分类列表成功，数量：{}", categories.size());
        return StreamUtils.toListVO(categories, FileCategoryVO.class);
    }

    @Override
    public List<FileCategoryVO> listAllCategories() {
        log.debug("开始查询所有分类列表");

        // DB查询该分类信息（所有分类）
        List<InfraFileCategory> categories = infraFileCategoryMapper.selectList(
                new LambdaQueryWrapper<InfraFileCategory>()
                        .orderByAsc(InfraFileCategory::getCategoryName)
        );

        log.debug("查询所有分类列表成功，数量：{}", categories.size());
        return StreamUtils.toListVO(categories, FileCategoryVO.class);
    }

    @Override
    public FileCategoryVO getCategoryDetail(Long categoryId) {
        log.debug("开始查询分类详情，分类ID：{}", categoryId);

        // DB查询该分类信息
        InfraFileCategory category = infraFileCategoryMapper.selectById(categoryId);
        if (category == null) {
            log.error("文件分类不存在，分类ID：{}", categoryId);
            throw new BusinessException(FileExceptionEnum.FILE_CATEGORY_NOT_FOUND);
        }

        log.debug("查询分类详情成功，分类ID：{}", category.getId());

        // 转换为VO并返回
        return BeanUtil.copyProperties(category, FileCategoryVO.class);
    }

    /**
     * 递归检查分类及其所有子分类是否存在文件，如果存在则抛出异常
     * 同时收集所有子分类ID用于后续批量删除
     *
     * @param categoryId 分类ID
     */
    private List<Long> getAllSubCategoryIdsAndCheckFiles(Long categoryId) {
        List<Long> allIds = new ArrayList<>();
        allIds.add(categoryId);

        // 查询当前分类的fileCount
        InfraFileCategory currentCategory = infraFileCategoryMapper.selectOne(
                new LambdaQueryWrapper<InfraFileCategory>()
                        .eq(InfraFileCategory::getId, categoryId)
                        .select(InfraFileCategory::getFileCount)
        );

        // 如果当前分类有文件，立即抛出异常
        if (currentCategory != null && currentCategory.getFileCount() != null && currentCategory.getFileCount() > 0) {
            log.error("分类下存在文件，无法删除，分类ID：{}，文件数量：{}", categoryId, currentCategory.getFileCount());
            throw new BusinessException(FileExceptionEnum.FILE_CATEGORY_HAS_FILES);
        }

        // 查询直接子分类
        List<InfraFileCategory> subCategories = infraFileCategoryMapper.selectList(
                new LambdaQueryWrapper<InfraFileCategory>()
                        .eq(InfraFileCategory::getParentId, categoryId)
                        .select(InfraFileCategory::getId, InfraFileCategory::getFileCount)
        );

        // 递归处理子分类
        for (InfraFileCategory subCategory : subCategories) {
            List<Long> subIds = getAllSubCategoryIdsAndCheckFiles(subCategory.getId());
            allIds.addAll(subIds);
        }

        return allIds;
    }
}
