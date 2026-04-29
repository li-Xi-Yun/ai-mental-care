package org.lixiyun.server.service.user;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.user.scale.ScaleUserAnswerDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleUserAnswerVO;
import org.lixiyun.pojo.vo.user.scale.ScaleUserRecordVO;

/**
 * @author lixiyun
 * @since 2026-04-17 13:49
 */
public interface ScaleUserService {

    /**
     * 用户提交测评答案
     *
     * @param answerDTO {@link ScaleUserAnswerDTO} 测评答案数据
     */
    void submitAnswer(ScaleUserAnswerDTO answerDTO);

    /**
     * 分页查询用户测评记录列表
     *
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @return 用户测评记录分页数据
     */
    PageResult<ScaleUserRecordVO> listRecords(Integer pageNum, Integer pageSize);

    /**
     * 分页查询用户测评答题明细列表
     *
     * @param recordId 测评记录ID
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @return 测评答题明细分页数据
     */
    PageResult<ScaleUserAnswerVO> listAnswerDetails(Long recordId, Integer pageNum, Integer pageSize);
}
