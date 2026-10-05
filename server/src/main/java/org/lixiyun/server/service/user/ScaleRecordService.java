package org.lixiyun.server.service.user;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.vo.user.scale.ScaleAnswerDetailVO;
import org.lixiyun.pojo.vo.user.scale.ScaleRecordDetailVO;
import org.lixiyun.pojo.vo.user.scale.ScaleRecordVO;

import java.util.List;

/**
 * 测评记录前台服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleRecordService {

    /**
     * 分页查询当前用户的测评历史
     * <p>仅返回 token 归属用户的记录，按开始时间倒序排列；支持按量表ID、完成状态过滤。</p>
     *
     * @param pageNum      页码（从 1 开始）
     * @param pageSize     每页条数
     * @param scaleId      量表ID，可为空
     * @param finishStatus 完成状态（0-未完成 1-已完成 2-中途终止），可为空
     * @return 测评记录分页结果
     */
    PageResult<ScaleRecordVO> listRecords(Integer pageNum, Integer pageSize, Long scaleId, Integer finishStatus);

    /**
     * 查询当前用户单次测评结果详情
     * <p>校验记录归属当前用户，结果全部取库内快照，不重新计算。</p>
     *
     * @param recordId 测评记录ID
     * @return 单次测评结果详情 VO
     */
    ScaleRecordDetailVO getRecordDetail(Long recordId);

    /**
     * 查询当前用户指定测评记录的答题明细
     * <p>校验记录归属当前用户，返回逐题明细快照，不含单题分数与耗时。</p>
     *
     * @param recordId 测评记录ID
     * @return 答题明细列表
     */
    List<ScaleAnswerDetailVO> listAnswerDetails(Long recordId);
}