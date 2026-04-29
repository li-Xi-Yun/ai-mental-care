package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQueryDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleDTO;
import org.lixiyun.pojo.vo.user.scale.ScaleVO;

/**
 * 管理员量表服务接口
 *
 * @author lixiyun
 * @since 2026-04-19 15:04
 */
public interface AdminScaleService {

    /**
     * 创建量表
     * <p>创建量表元数据信息(不包含题目、选项、规则)，如果指定了分类ID，则更新分类的使用数量</p>
     *
     * @param scaleDTO 量表DTO {@link ScaleDTO}
     */
    void createScale(ScaleDTO scaleDTO);

    /**
     * 分页查询量表列表（支持多条件查询）
     * <p>支持删除状态、启用状态、名称模糊匹配等查询条件</p>
     *
     * @param queryDTO 查询条件（包含分页参数） {@link AdminScaleQueryDTO}
     * @return 分页结果
     */
    PageResult<ScaleVO> listScales(AdminScaleQueryDTO queryDTO);

    /**
     * 删除量表
     * <p>级联删除量表及其题目、选项、规则</p>
     *
     * @param scaleId 量表ID
     */
    void deleteScale(Long scaleId);

    /**
     * 修改量表
     * <p>修改量表的元数据信息</p>
     *
     * @param scaleId 量表ID
     * @param dto 量表DTO {@link ScaleDTO}
     */
    void updateScale(Long scaleId, ScaleDTO dto);

    /**
     * 恢复已删除的量表
     * <p>级联恢复量表及其题目、选项、规则，将删除状态从1改为0</p>
     *
     * @param scaleId 量表ID
     */
    void restoreScale(Long scaleId);
}
