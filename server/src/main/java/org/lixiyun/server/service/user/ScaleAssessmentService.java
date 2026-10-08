package org.lixiyun.server.service.user;

import org.lixiyun.pojo.dto.user.scale.ScaleResumeDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleStartDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleSubmitDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleStartVO;
import org.lixiyun.pojo.vo.user.scale.ScaleSubmitResultVO;
import org.lixiyun.pojo.vo.user.scale.ScaleUnfinishedVO;

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
     * 查询当前用户指定量表最近一条未完成测评记录
     * <p>通用量表入口使用：前端进入答题前先调用本方法，判断是提示"继续作答"还是直接新建。</p>
     *
     * @param scaleId 量表ID
     * @return 未完成测评信息；不存在未完成记录时 hasUnfinished=false
     */
    ScaleUnfinishedVO getUnfinished(Long scaleId);

    /**
     * 续答未完成测评
     * <p>校验记录归属且处于未完成状态后，按记录锁定版本下发整卷题目，不新建记录、不修改任何作答数据。</p>
     *
     * @param dto 续答测评请求 DTO（含 recordId）
     * @return 开始测评返回 VO（含原 recordId 与整卷题目数据）
     */
    ScaleStartVO resume(ScaleResumeDTO dto);

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