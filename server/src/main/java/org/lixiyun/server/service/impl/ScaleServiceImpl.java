package org.lixiyun.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.scale.ScaleDTO;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.entity.scale.ScaleResultRule;
import org.lixiyun.pojo.vo.scale.ScaleVO;
import org.lixiyun.server.mapper.*;
import org.lixiyun.server.service.ScaleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author lixiyun
 * @since 2026-04-15 09:24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleServiceImpl implements ScaleService {

    private final ScaleMapper scaleMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleOptionMapper scaleOptionMapper;
    private final ScaleResultRuleMapper scaleResultRuleMapper;
    private final ScaleCategoryMapper scaleCategoryMapper;

    @Override
    @Transactional
    public void createScale(ScaleDTO scaleDTO) {
        // 插入量表主表
        Scale scale = new Scale();
        BeanUtil.copyProperties(scaleDTO, scale);
        scaleMapper.insert(scale);
        Long scaleId = scale.getId();
        if(scaleDTO.getScaleCategoryId() != null) {
            // 如果指定了分类，则更新分类的使用数量(加一)
            scaleCategoryMapper.updateUserCount(scaleDTO.getScaleCategoryId(), 1);
        }

        log.info("量表创建成功，ID: {}", scaleId);
    }

    @Override
    public PageResult<ScaleVO> listScales(Integer pageNum, Integer pageSize, Long scaleCategoryId) {
        Page<Scale> build = new PageQuery(pageSize, pageNum).build();
        Page<Scale> scalePage = scaleMapper.selectPage(build, new LambdaQueryWrapper<Scale>()
                .eq(Scale::getScaleCategoryId, scaleCategoryId));
        return PageResult.convert(scalePage, ScaleVO.class);
    }

    @Override
    @Transactional
    public void deleteScale(Long scaleId) {
        // 级联删除：先删除结果规则，再删除选项，再删除题目，最后删除量表
        scaleResultRuleMapper.delete(new LambdaQueryWrapper<ScaleResultRule>()
                .eq(ScaleResultRule::getScaleId, scaleId));
        scaleOptionMapper.delete(new LambdaQueryWrapper<ScaleOption>()
                .inSql(ScaleOption::getQuestionId, "SELECT id FROM scale_question WHERE scale_id = " + scaleId));
        scaleQuestionMapper.delete(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleId, scaleId));
        scaleMapper.deleteById(scaleId);
        log.info("量表删除成功，ID: {}", scaleId);
    }

    @Override
    public void updateScale(Long scaleId, ScaleDTO dto) {
        Scale scale = new Scale();
        BeanUtil.copyProperties(dto, scale);
        scale.setId(scaleId);
        scaleMapper.updateById(scale);
        log.info("量表修改成功，scaleId: {}", scaleId);
    }
}
