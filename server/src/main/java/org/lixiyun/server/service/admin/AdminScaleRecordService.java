package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleRecordQueryDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleRiskStatisticsQueryDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleAnswerDetailVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleRecordDetailVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleRecordVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleRiskStatisticsVO;

import java.util.List;

/**
 * 管理员量表测评记录监控服务接口（敏感数据，须严格权限与审计）
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleRecordService {

    /**
     * 分页查询用户测评记录（筛选：userId/scaleId/scaleName/riskLevel/finishStatus/时间范围）
     *
     * @param queryDTO 查询条件 {@link AdminScaleRecordQueryDTO}
     * @return 测评记录分页结果
     */
    PageResult<AdminScaleRecordVO> pageRecords(AdminScaleRecordQueryDTO queryDTO);

    /**
     * 获取测评记录详情（含维度分、所用版本、常模组）
     *
     * @param recordId 测评记录ID
     * @return 测评记录详情
     */
    AdminScaleRecordDetailVO getRecordDetail(Long recordId);

    /**
     * 获取测评记录答题明细（题目/选项/填空快照，不含单题原始分与耗时）
     *
     * @param recordId 测评记录ID
     * @return 答题明细列表
     */
    List<AdminScaleAnswerDetailVO> listAnswerDetails(Long recordId);

    /**
     * 风险预警统计（按量表统计各风险等级人次，高风险占比为派生字段）
     *
     * @param queryDTO 统计查询条件 {@link AdminScaleRiskStatisticsQueryDTO}
     * @return 风险预警统计结果
     */
    AdminScaleRiskStatisticsVO riskStatistics(AdminScaleRiskStatisticsQueryDTO queryDTO);
}