package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.scale.AdminScaleQuestionDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQuestionSortItemDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleQuestionVO;

import java.util.List;

/**
 * 管理员量表题目服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleQuestionService {

    /**
     * 查询版本题目全量列表（含选项，按 sort 排序）
     *
     * @param scaleVersionId 量表版本ID
     * @return 题目列表
     */
    List<AdminScaleQuestionVO> listQuestions(Long scaleVersionId);

    /**
     * 获取题目详情（含选项）
     *
     * @param questionId 题目ID
     * @return 题目详情
     */
    AdminScaleQuestionVO getQuestionDetail(Long questionId);

    /**
     * 新增题目（可内联选项）
     *
     * @param dto 题目DTO {@link AdminScaleQuestionDTO}
     */
    void createQuestion(AdminScaleQuestionDTO dto);

    /**
     * 修改题目（可整体替换选项）
     *
     * @param questionId 题目ID
     * @param dto        题目DTO {@link AdminScaleQuestionDTO}
     */
    void updateQuestion(Long questionId, AdminScaleQuestionDTO dto);

    /**
     * 批量调整题目顺序
     *
     * @param sortItems 排序项列表 {@link AdminScaleQuestionSortItemDTO}
     */
    void updateQuestionSort(List<AdminScaleQuestionSortItemDTO> sortItems);

    /**
     * 调整题目所属维度（dimensionId 为空表示移除维度）
     *
     * @param questionId  题目ID
     * @param dimensionId 目标维度ID，可空
     */
    void updateQuestionDimension(Long questionId, Long dimensionId);

    /**
     * 复制题目到指定版本
     *
     * @param questionId          源题目ID
     * @param targetScaleVersionId 目标量表版本ID
     */
    void copyQuestion(Long questionId, Long targetScaleVersionId);

    /**
     * 逻辑删除题目（级联选项，有跳题规则引用时提示同步清理）
     *
     * @param questionId 题目ID
     */
    void deleteQuestion(Long questionId);
}