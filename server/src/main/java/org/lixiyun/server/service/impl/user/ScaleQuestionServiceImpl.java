package org.lixiyun.server.service.impl.user;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.constant.DeleteConstant;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.vo.user.scale.ScaleOptionVO;
import org.lixiyun.pojo.vo.user.scale.ScaleQuestionVO;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.service.user.ScaleQuestionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author lixiyun
 * @since 2026-04-15 16:27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleQuestionServiceImpl implements ScaleQuestionService {

    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleOptionMapper scaleOptionMapper;
    private final ScaleMapper scaleMapper;

    @Override
    public PageResult<ScaleQuestionVO> listQuestionsWithOptions(Long scaleId, Integer pageNum, Integer pageSize) {
        // 检查量表是否存在且启用
        Scale scale = scaleMapper.selectById(scaleId);
        if (scale == null || !scale.isEnabled() || scale.deleteFlat()) {
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }

        // 构建分页对象
        Page<ScaleQuestion> pageParam = new PageQuery(pageSize, pageNum).build();

        // 查询指定量表下的题目，按排序字段升序排列
        LambdaQueryWrapper<ScaleQuestion> wrapper = new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleId, scaleId)
                .eq(ScaleQuestion::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByAsc(ScaleQuestion::getSort);

        Page<ScaleQuestion> questionPage = scaleQuestionMapper.selectPage(pageParam, wrapper);

        // 获取当前页的所有题目ID
        List<Long> questionIds = questionPage.getRecords().stream()
                .map(ScaleQuestion::getId)
                .toList();

        // 批量查询这些题目的所有选项
        Map<Long, List<ScaleOption>> optionMap = Map.of();
        if (!questionIds.isEmpty()) {
            List<ScaleOption> allOptions = scaleOptionMapper.selectList(new LambdaQueryWrapper<ScaleOption>()
                    .in(ScaleOption::getQuestionId, questionIds)
                    .orderByAsc(ScaleOption::getSort));

            // 使用Stream将选项按题目ID分组
            optionMap = allOptions.stream()
                    .collect(Collectors.groupingBy(ScaleOption::getQuestionId));
        }

        // 使用Stream将题目和选项组合成VO
        final Map<Long, List<ScaleOption>> finalOptionMap = optionMap;
        List<ScaleQuestionVO> voList = questionPage.getRecords().stream()
                .map(question -> {
                    ScaleQuestionVO vo = BeanUtil.copyProperties(question, ScaleQuestionVO.class);
                    List<ScaleOption> options = finalOptionMap.getOrDefault(question.getId(), List.of());
                    List<ScaleOptionVO> optionVOs = options.stream()
                            .map(option -> ScaleOptionVO.builder()
                                    .id(option.getId())
                                    .optionText(option.getOptionText())
                                    .score(option.getScore())
                                    .sort(option.getSort())
                                    .build())
                            .toList();
                    vo.setOptionList(optionVOs);
                    return vo;
                })
                .toList();

        // 返回分页结果
        return new PageResult<>(questionPage.getTotal(), voList);
    }

}
