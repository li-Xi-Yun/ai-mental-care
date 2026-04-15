package org.lixiyun.server.service;

import org.lixiyun.pojo.dto.scale.ScaleQuestionDTO;
import org.lixiyun.pojo.vo.scale.ScaleQuestionVO;

/**
 * @author lixiyun
 * @since 2026-04-15 16:26
 */
public interface ScaleQuestionService {

    /**
     * 创建一个问题及其选项。
     *
     * @param scaleQuestionDTO 包含问题和选项信息的DTO
     */
    void createWithOptions(ScaleQuestionDTO scaleQuestionDTO);

    /**
     * 检索一个问题及其选项。
     *
     * @param questionId 要检索的问题的ID
     * @return 包含问题和选项数据的ScaleQuestionVO
     */
    ScaleQuestionVO getWithOptions(Long questionId);

    /**
     * 删除一个问题及其选项。
     *
     * @param questionId 要删除的问题的ID
     * @param scaleId    问题所属量表的ID
     */
    void deleteWithOptions(Long questionId, Long scaleId);

    /**
     * 更新问题的元数据。
     *
     * @param questionId       要更新的问题的ID
     * @param scaleQuestionDTO 包含更新的问题元数据的DTO
     */
    void updateMetadata(Long questionId, ScaleQuestionDTO scaleQuestionDTO);
}
