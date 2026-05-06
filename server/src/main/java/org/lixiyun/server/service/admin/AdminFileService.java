package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.file.FileQueryDTO;
import org.lixiyun.pojo.dto.admin.file.FileUpdateDTO;
import org.lixiyun.pojo.vo.admin.file.FileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件管理服务接口
 *
 * @author lixiyun
 * @since 2026-05-01
 */
public interface AdminFileService {

    /**
     * 文件上传
     * <p>判断文件大小，获取文件MD5，查询文件是否已存在，本地文件存储，DB存储文件元数据信息</p>
     * <p>如果文件已存在且已删除，则恢复文件并恢复向量删除元数据信息</p>
     *
     * @param file 文件对象
     * @param categoryId 分类ID（可选）
     * @return 文件元数据信息 {@link FileVO}
     */
    FileVO uploadFile(MultipartFile file, Long categoryId);

    /**
     * 文件分页查询
     * <p>DB分页查询该分类下所有文件数据</p>
     *
     * @param queryDTO 文件查询DTO {@link FileQueryDTO}
     * @return 文件元数据信息分页结果
     */
    PageResult<FileVO> queryFilePage(FileQueryDTO queryDTO);

    /**
     * 文件删除
     * <p>查询文件信息，校验文件存在和删除状态，逻辑删除文件元数据信息</p>
     * <p>根据文件状态处理向量数据：待解析/解析失败-直接结束；解析中-中断解析；解析完成-添加删除元数据</p>
     *
     * @param fileId 文件ID
     */
    void deleteFile(Long fileId);

    /**
     * 文件元数据信息修改
     * <p>DB修改文件元数据信息，DB查询文件元数据信息</p>
     *
     * @param updateDTO 文件信息修改DTO {@link FileUpdateDTO}
     * @return 文件元数据信息
     */
    FileVO updateFileInfo(FileUpdateDTO updateDTO);
}
