package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.vo.admin.file.FileVectorVO;

/**
 * 文件向量管理服务接口
 * <p>提供文件向量加载、删除、查询和解析中断等功能</p>
 *
 * @author lixiyun
 * @since 2026-05-02
 */
public interface AdminFileVectorService {

    /**
     * 文件向量加载
     * <p>根据文件ID获取文件元数据，校验文件删除状态和文件状态，获取文件元数据后修改状态为解析中，然后进行向量加载</p>
     * <p>向量加载采用异步方式执行，加载完成后自动更新文件状态为解析完成，加载失败则更新为解析失败</p>
     *
     * @param fileId 文件ID，不能为空
     * @throws org.lixiyun.common.core.error.exception.BusinessException 当文件不存在、已删除或状态不允许时抛出
     */
    void loadFileVector(Long fileId);

    /**
     * 文件向量删除
     * <p>根据文件ID查询文件是否存在，校验文件删除状态和文件状态，然后进行文件向量解析中断，最后物理删除向量数据</p>
     * <p>删除完成后会将文件状态重置为待解析</p>
     *
     * @param fileId 文件ID，不能为空
     * @throws org.lixiyun.common.core.error.exception.BusinessException 当文件不存在、已删除或状态不允许时抛出
     */
    void deleteFileVector(Long fileId);

    /**
     * 文件向量分页查询
     * <p>根据文件ID查询文件是否存在，校验文件删除状态和文件状态，然后根据文件ID查询向量集合，按二次分块索引排序分页返回</p>
     * <p>如果文件未解析完成，则返回空分页结果</p>
     *
     * @param fileId 文件ID，不能为空
      * @param pageNum 页码，不能为空
      * @param pageSize 每页数量，不能为空
     * @return 文件向量分页数据 {@link FileVectorVO}
     * @throws org.lixiyun.common.core.error.exception.BusinessException 当文件不存在或已删除时抛出
     */
    PageResult<FileVectorVO> queryFileVector(Long fileId, Integer pageNum, Integer pageSize);

    /**
     * 文件向量解析中断
     * <p>从RagFileThreadHolder获取线程对象，判断是否为null，如果为null则直接返回，否则设置线程中断标识</p>
     * <p>中断处理会触发私有辅助方法handleInterrupt，物理删除已加载的向量数据并更新文件状态</p>
     *
     * @param fileId 文件ID，不能为空
     */
    void interruptFileVectorParsing(Long fileId);

}
