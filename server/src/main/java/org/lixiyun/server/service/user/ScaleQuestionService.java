package org.lixiyun.server.service.user;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.vo.user.scale.ScaleQuestionVO;

/**
 * @author lixiyun
 * @since 2026-04-15 16:26
 */
public interface ScaleQuestionService {

    /**
     * 分页查询指定量表下的题目列表及选项。
     *
     * @param scaleId  量表ID
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @return 包含题目和选项数据的分页结果 {@link ScaleQuestionVO}
     */
    PageResult<ScaleQuestionVO> listQuestionsWithOptions(Long scaleId, Integer pageNum, Integer pageSize);

}
