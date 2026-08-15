package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.symptom.AdminSymptomDictQueryDTO;
import org.lixiyun.pojo.dto.admin.symptom.SymptomDictDTO;
import org.lixiyun.pojo.vo.admin.symptom.SymptomDictOptionVO;
import org.lixiyun.pojo.vo.admin.symptom.SymptomDictVO;

import java.util.List;

/**
 * 管理员症状词典服务接口
 *
 * @author lixiyun
 * @since 2026-08-15
 */
public interface AdminSymptomDictService {

    /**
     * 分页查询症状词典
     *
     * @param queryDTO 查询条件 {@link AdminSymptomDictQueryDTO}
     * @return 分页结果
     */
    PageResult<SymptomDictVO> pageSymptomDict(AdminSymptomDictQueryDTO queryDTO);

    /**
     * 获取症状词典详情
     *
     * @param id 标准术语ID
     * @return 症状词典详情
     */
    SymptomDictVO getSymptomDictDetail(Long id);

    /**
     * 新增症状词典
     *
     * @param dto 症状词典DTO {@link SymptomDictDTO}
     */
    void createSymptomDict(SymptomDictDTO dto);

    /**
     * 修改症状词典
     *
     * @param id  标准术语ID
     * @param dto 症状词典DTO {@link SymptomDictDTO}
     */
    void updateSymptomDict(Long id, SymptomDictDTO dto);

    /**
     * 启用/禁用症状词典
     *
     * @param id       标准术语ID
     * @param status 状态DTO
     */
    void updateSymptomDictStatus(Long id, Integer status);

    /**
     * 批量删除症状词典
     *
     * @param ids 标准术语ID列表
     */
    void batchDeleteSymptomDict(List<Long> ids);

    /**
     * 获取症状词典下拉选项（按分类分组，只返回启用的数据）
     *
     * @return 分组后的下拉选项列表
     */
    List<SymptomDictOptionVO> getSymptomDictOptions();
}