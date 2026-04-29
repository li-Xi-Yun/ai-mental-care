package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.user.scale.ScaleQuestionDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleQuestionVO;

/**
 * @author lixiyun
 * @since 2026-04-19 15:37
 */
public interface AdminScaleQuestionService {

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
     * @return 包含问题和选项数据的ScaleQuestionVO {@link ScaleQuestionVO}
     */
    ScaleQuestionVO getWithOptions(Long questionId);

    /**
     * 分页查询指定量表下的题目列表及选项。
     *
     * @param scaleId  量表ID
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @return 包含题目和选项数据的分页结果 {@link ScaleQuestionVO}
     */
    PageResult<ScaleQuestionVO> listQuestionsWithOptions(Long scaleId, Integer pageNum, Integer pageSize);

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
