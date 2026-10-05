package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQueryDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleStatusDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleDetailVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleVO;

/**
 * 管理员量表主表服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleService {

    /**
     * 新增量表主表（可同时创建并发布首个版本）
     *
     * @param dto 量表DTO {@link AdminScaleDTO}
     */
    void createScale(AdminScaleDTO dto);

    /**
     * 分页查询量表主表
     *
     * @param queryDTO 查询条件 {@link AdminScaleQueryDTO}
     * @return 量表分页结果
     */
    PageResult<AdminScaleVO> pageScales(AdminScaleQueryDTO queryDTO);

    /**
     * 获取量表详情（含当前版本摘要与全部版本列表）
     *
     * @param scaleId 量表ID
     * @return 量表详情
     */
    AdminScaleDetailVO getScaleDetail(Long scaleId);

    /**
     * 修改量表基本元数据
     *
     * @param scaleId 量表ID
     * @param dto     量表DTO {@link AdminScaleDTO}
     */
    void updateScale(Long scaleId, AdminScaleDTO dto);

    /**
     * 启用/禁用量表
     *
     * @param scaleId 量表ID
     * @param dto     状态DTO {@link AdminScaleStatusDTO}
     */
    void updateScaleStatus(Long scaleId, AdminScaleStatusDTO dto);

    /**
     * 逻辑删除量表（级联逻辑删除其全部版本及内容，有测评记录时仅提示）
     *
     * @param scaleId 量表ID
     */
    void deleteScale(Long scaleId);

    /**
     * 级联恢复已删除的量表及其版本与内容
     *
     * @param scaleId 量表ID
     */
    void restoreScale(Long scaleId);
}