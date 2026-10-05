package org.lixiyun.server.service.user;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.vo.user.scale.ScaleDetailVO;
import org.lixiyun.pojo.vo.user.scale.ScalePrecheckVO;
import org.lixiyun.pojo.vo.user.scale.ScaleVO;

/**
 * 量表前台服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleService {

    /**
     * 分页查询可作答量表列表
     * <p>服务端强制过滤：deleted=0 且 status=1 且当前版本存在且未删除；支持按量表类别、名称关键字过滤。</p>
     *
     * @param pageNum         页码（从 1 开始）
     * @param pageSize        每页条数
     * @param scaleCategoryId 量表类别ID，可为空
     * @param keyword         量表名称关键字，可为空
     * @return 量表列表分页结果，每条含当前版本聚合信息
     */
    PageResult<ScaleVO> listScales(Integer pageNum, Integer pageSize, Long scaleCategoryId, String keyword);

    /**
     * 查询量表详情
     * <p>校验量表启用且未删除、当前版本存在，并返回当前版本的维度构成与题型统计。</p>
     *
     * @param scaleId 量表ID
     * @return 量表详情 VO
     */
    ScaleDetailVO getScaleDetail(Long scaleId);

    /**
     * 作答前检查
     * <p>校验量表是否有效、冷却时间、重复作答限制及是否存在未完成记录，返回是否允许开始及提示信息。</p>
     *
     * @param scaleId 量表ID
     * @return 作答前检查 VO
     */
    ScalePrecheckVO precheck(Long scaleId);
}