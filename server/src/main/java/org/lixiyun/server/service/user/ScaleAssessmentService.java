package org.lixiyun.server.service.user;

import org.lixiyun.pojo.dto.user.scale.ScaleStartDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleSubmitDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleStartVO;
import org.lixiyun.pojo.vo.user.scale.ScaleSubmitResultVO;

/**
 * 量表测评前台服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleAssessmentService {

    /**
     * 开始测评
     * <p>服务端复检作答条件、将旧的未完成记录置为终止并新建测评记录（锁定当前版本），一次性下发整卷题目与精简跳题规则。</p>
     *
     * @param dto 开始测评请求 DTO
     * @return 开始测评返回 VO（含 recordId 与整卷题目数据）
     */
    ScaleStartVO start(ScaleStartDTO dto);

    /**
     * 提交测评答案并计算结果
     * <p>服务端完成归属校验、超时校验、作答路径合法性校验、答案落库、计分、结果规则匹配与常模换算，产出并保存结果。</p>
     *
     * @param dto 提交测评答案请求 DTO
     * @return 提交测评结果 VO
     */
    ScaleSubmitResultVO submit(ScaleSubmitDTO dto);

    /**
     * 主动终止测评
     * <p>校验记录归属后，将记录置为中途终止（finish_status=2）并记录结束时间，已答内容保留。</p>
     *
     * @param recordId 测评记录ID
     */
    void terminate(Long recordId);
}