package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.FileExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.file.storage.FileStorage;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.file.FileQueryDTO;
import org.lixiyun.pojo.dto.admin.file.FileUpdateDTO;
import org.lixiyun.pojo.entity.file.InfraFile;
import org.lixiyun.pojo.entity.file.InfraFileCategory;
import org.lixiyun.pojo.vo.admin.file.FileVO;
import org.lixiyun.server.ai.rag.RagStore;
import org.lixiyun.server.mapper.InfraFileCategoryMapper;
import org.lixiyun.server.mapper.InfraFileMapper;
import org.lixiyun.server.service.admin.AdminFileService;
import org.lixiyun.server.service.admin.AdminFileVectorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件管理服务实现类
 *
 * @author lixiyun
 * @since 2026-05-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFileServiceImpl implements AdminFileService {

    private final InfraFileMapper infraFileMapper;
    private final InfraFileCategoryMapper infraFileCategoryMapper;
    private final FileStorage fileStorage;
    private final RagStore ragStore;
    private final AdminFileVectorService adminFileVectorService;

    /**
     * 最大文件大小
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO uploadFile(MultipartFile file, Long categoryId) {
        log.info("开始上传文件，文件名：{}，文件大小：{}字节，分类ID：{}", file.getOriginalFilename(), file.getSize(), categoryId);

        // 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            log.error("文件大小超出限制，当前大小：{}字节，最大限制：{}字节", file.getSize(), MAX_FILE_SIZE);
            throw new BusinessException(FileExceptionEnum.FILE_SIZE_EXCEEDED);
        }

        // 获取文件内容MD5
        String fileMd5;
        try {
            fileMd5 = SecureUtil.md5(file.getInputStream());
        } catch (IOException e) {
            log.error("计算文件MD5失败", e);
            throw new BusinessException(FileExceptionEnum.FILE_READ_ERROR);
        }
        log.debug("文件MD5：{}", fileMd5);

        // DB查询文件，根据MD5判断文件是否已存在
        InfraFile existingFile = infraFileMapper.selectMyFileByFileMd5(fileMd5);

        // 文件已存在，判断删除状态
        if (existingFile != null) {
            if (existingFile.deleteFlat()) {
                // 文件已被逻辑删除，恢复文件
                log.info("文件已存在但已被删除，恢复文件，文件ID：{}", existingFile.getId());
                infraFileMapper.update(null,
                        new LambdaUpdateWrapper<InfraFile>()
                                .eq(InfraFile::getId, existingFile.getId())
                                .set(InfraFile::getDeleted, org.lixiyun.pojo.constant.DeleteConstant.DELETE_FLAG_NO)
                );
                log.info("文件恢复成功，文件ID：{}", existingFile.getId());
                infraFileCategoryMapper.updateMyFileCount(existingFile.getCategoryId(), 1);
                log.info("文件分类数量更新完成，分类ID：{}，数量变化：{}", existingFile.getCategoryId(), 1);
                
                // 根据文件ID，向量恢复删除元数据信息
                try {
                    ragStore.restoreVectorDeletedMetadata(existingFile.getId());
                } catch (Exception e) {
                    log.error("恢复向量删除元数据信息失败，文件ID：{}", existingFile.getId(), e);
                    // 静默处理，不影响主流程
                }
            } else {
                // 文件存在且未删除，直接返回
                log.info("文件已存在，直接返回，文件ID：{}", existingFile.getId());
            }
            return BeanUtil.copyProperties(existingFile, FileVO.class);
        }

        // 获取当前操作人ID
        Long personId = UserInfoThreadLocalUtil.getCurrentIdThrow();
        log.debug("当前操作人ID：{}", personId);

        // 判断分类ID，如果为空或无效则使用默认分类
        Long finalCategoryId = categoryId;
        if (finalCategoryId == null) {
            finalCategoryId = InfraFileCategory.DEFAULT_CATEGORY_ID;
            log.debug("使用默认分类ID：{}", finalCategoryId);
        }

        // 本地文件存储（复用FileStorage工具类）
        String fileUrl = fileStorage.localFileStorage(file);
        log.debug("文件本地存储成功，路径：{}", fileUrl);

        // 构建文件元数据实体
        String originalFilename = file.getOriginalFilename();
        String fileSuffix = StrUtil.isNotBlank(originalFilename) && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".") + 1)
                : "";

        InfraFile infraFile = InfraFile.builder()
                .personId(personId)
                .categoryId(finalCategoryId)
                .originalName(originalFilename)
                .fileUrl(fileUrl)
                .fileSuffix(fileSuffix)
                .fileSize(file.getSize())
                .fileMd5(fileMd5)
                .status(InfraFile.STATUS_PENDING)
                .build();

        // DB存储文件元数据信息
        infraFileMapper.insert(infraFile);
        log.info("文件元数据入库成功，文件ID：{}", infraFile.getId());

        infraFileCategoryMapper.updateMyFileCount(finalCategoryId, 1);
        log.info("文件分类数量更新完成，分类ID：{}，数量变化：{}", infraFile.getCategoryId(), 1);

        // 转换为VO并返回
        return BeanUtil.copyProperties(infraFile, FileVO.class);
    }

    @Override
    public PageResult<FileVO> queryFilePage(FileQueryDTO queryDTO) {
        log.debug("开始分页查询文件，查询条件：{}", queryDTO);

        // 构建分页对象
        Page<InfraFile> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());

        // 构建查询条件
        LambdaQueryWrapper<InfraFile> queryWrapper = new LambdaQueryWrapper<>();
        
        // 分类ID过滤
        if (queryDTO.getCategoryId() != null) {
            queryWrapper.eq(InfraFile::getCategoryId, queryDTO.getCategoryId());
        }

        // 文件名模糊查询
        if (StrUtil.isNotBlank(queryDTO.getFileName())) {
            queryWrapper.like(InfraFile::getOriginalName, queryDTO.getFileName());
        }

        // 文件状态过滤
        if (queryDTO.getStatus() != null) {
            queryWrapper.eq(InfraFile::getStatus, queryDTO.getStatus());
        }

        // 开始时间过滤
        if (queryDTO.getStartTime() != null) {
            queryWrapper.ge(InfraFile::getCreatedTime, queryDTO.getStartTime());
        }

        // 结束时间过滤
        if (queryDTO.getEndTime() != null) {
            queryWrapper.le(InfraFile::getCreatedTime, queryDTO.getEndTime());
        }

        // 按创建时间倒序排列
        queryWrapper.orderByDesc(InfraFile::getCreatedTime);

        // DB分页查询
        Page<InfraFile> resultPage = infraFileMapper.selectPage(page, queryWrapper);
        log.debug("文件分页查询完成，总数：{}，当前页记录数：{}", resultPage.getTotal(), resultPage.getRecords().size());

        // 转换为VO并返回分页结果
        return PageResult.convert(resultPage, FileVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long fileId) {
        log.info("开始删除文件，文件ID：{}", fileId);

        // 查询文件元数据，校验文件是否存在
        InfraFile infraFile = infraFileMapper.selectById(fileId);
        if (infraFile == null) {
            log.error("文件删除-文件不存在，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }

        // 校验文件删除状态
        if (infraFile.deleteFlat()) {
            log.error("文件删除-文件已删除，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }

        // 逻辑删除文件元数据
        infraFileMapper.deleteById(fileId);
        log.info("文件元数据删除成功，文件ID：{}", fileId);

        // 修改文件分类的文件数量
        infraFileCategoryMapper.updateMyFileCount(infraFile.getCategoryId(), -1);
        log.info("文件分类数量更新完成，分类ID：{}，数量变化：{}", infraFile.getCategoryId(), -1);

        // 根据文件状态处理向量数据
        if (infraFile.isPending() || infraFile.isParseFailed()) {
            // 待解析或解析失败，直接结束
            log.debug("文件状态为待解析或解析失败，无需处理向量数据，文件ID：{}", fileId);
        } else if (infraFile.isParsing()) {
            // 解析中，中断文件向量解析（静默处理异常）
            log.info("文件状态为解析中，尝试中断向量解析，文件ID：{}", fileId);
            try {
                adminFileVectorService.interruptFileVectorParsing(fileId);
                log.info("文件向量解析中断成功，文件ID：{}", fileId);
            } catch (Exception e) {
                log.error("文件向量解析中断失败，文件ID：{}", fileId, e);
                // 静默处理，不影响主流程
            }
        } else if (infraFile.isParseCompleted()) {
            // 解析完成，根据文件ID，向量添加删除元数据信息
            log.info("文件状态为解析完成，添加向量删除元数据，文件ID：{}", fileId);
            try {
                ragStore.addVectorDeletedMetadata(fileId);
                log.info("向量删除元数据添加成功，文件ID：{}", fileId);
            } catch (Exception e) {
                log.error("添加向量删除元数据失败，文件ID：{}", fileId, e);
                // 静默处理，不影响主流程
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO updateFileInfo(FileUpdateDTO updateDTO) {
        log.info("开始修改文件元数据，文件ID：{}，修改内容：{}", updateDTO.getId(), updateDTO);
        Long fileId = updateDTO.getId();
        String fileName = updateDTO.getFileName();
        Long categoryId = updateDTO.getCategoryId();
        Integer vectorStatus = updateDTO.getVectorStatus();

        InfraFile infraFileBeforeUpdate = infraFileMapper.selectById(fileId);
        if (infraFileBeforeUpdate == null) {
            log.error("文件不存在，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }
        Long categoryIdBeforeUpdate = infraFileBeforeUpdate.getCategoryId();
        Integer vectorStatusBeforeUpdate = infraFileBeforeUpdate.getVectorStatus();

        InfraFile updateEntity = new InfraFile();
        updateEntity.setId(fileId);

        boolean hasUpdate = false;
        if (StrUtil.isNotBlank(fileName)) {
            updateEntity.setOriginalName(fileName);
            hasUpdate = true;
        }
        if (categoryId != null) {
            updateEntity.setCategoryId(categoryId);
            hasUpdate = true;
        }
        if (vectorStatus != null) {
            updateEntity.setVectorStatus(vectorStatus);
            hasUpdate = true;
        }

        if (!hasUpdate) {
            log.warn("没有需要修改的字段，文件ID：{}", fileId);
        } else {
            int updated = infraFileMapper.updateById(updateEntity);
            if (updated == 0) {
                log.error("文件元数据修改失败，文件ID：{}", fileId);
                throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
            }
            log.debug("文件元数据修改成功");
        }

        if (categoryId != null && !categoryId.equals(categoryIdBeforeUpdate)) {
            infraFileCategoryMapper.updateMyFileCount(categoryIdBeforeUpdate, -1);
            infraFileCategoryMapper.updateMyFileCount(categoryId, 1);
            log.info("文件分类数量更新完成，分类ID：{}, {}，数量变化：{}, {}", categoryIdBeforeUpdate, categoryId, -1, 1);
        }

        if (vectorStatus != null && !vectorStatus.equals(vectorStatusBeforeUpdate)
                && InfraFile.VECTOR_STATUS_DISABLE == vectorStatus
                && InfraFile.VECTOR_STATUS_ENABLE == vectorStatusBeforeUpdate) {
            try {
                ragStore.addVectorDeletedMetadata(fileId);
                log.info("向量状态从启用变为禁用，已更新删除状态，文件ID：{}", fileId);
            } catch (Exception e) {
                log.error("更新向量删除状态失败，文件ID：{}", fileId, e);
            }
        }

        InfraFile infraFile = infraFileMapper.selectById(fileId);
        if (infraFile == null) {
            log.error("文件不存在，文件ID：{}", fileId);
            throw new BusinessException(FileExceptionEnum.FILE_NOT_FOUND);
        }

        log.info("文件元数据查询成功，文件ID：{}", infraFile.getId());
        return BeanUtil.copyProperties(infraFile, FileVO.class);
    }

}