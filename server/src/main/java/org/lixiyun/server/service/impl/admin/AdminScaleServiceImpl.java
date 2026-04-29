package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.constant.DeleteConstant;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleQueryDTO;
import org.lixiyun.pojo.dto.user.scale.ScaleDTO;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.entity.scale.ScaleResultRule;
import org.lixiyun.pojo.vo.user.scale.ScaleVO;
import org.lixiyun.server.mapper.*;
import org.lixiyun.server.service.admin.AdminScaleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-19 15:04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleServiceImpl implements AdminScaleService {

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
    public PageResult<ScaleVO> listScales(AdminScaleQueryDTO queryDTO) {
        log.debug("开始查询量表列表，查询条件: {}", queryDTO);

        PageHelper.startPage(queryDTO.getPageNum(), queryDTO.getPageSize());
        List<Scale> scaleList = scaleMapper.selectMyPage(queryDTO.getScaleName(), queryDTO.getIsDeleted(), queryDTO.getStatus());

        PageInfo<Scale> pageInfo = new PageInfo<>(scaleList);

        // 转换为 VO 并返回分页结果
        PageResult<ScaleVO> result = PageResult.convert(pageInfo, ScaleVO.class);

        log.debug("量表列表查询完成，总数: {}, 当前页记录数: {}", result.getTotal(), result.getRecords().size());
        return result;
    }


    @Override
    @Transactional
    public void deleteScale(Long scaleId) {
        // 设置状态为禁用
        scaleMapper.updateById(Scale.builder().id(scaleId).status(Scale.STATUS_DISABLE).build());

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
    @Transactional
    public void updateScale(Long scaleId, ScaleDTO dto) {
        Scale scale = new Scale();
        BeanUtil.copyProperties(dto, scale);
        scale.setId(scaleId);

        if(dto .getStatus() != null && dto.getStatus().equals(Scale.STATUS_ENABLE)){
            Scale scale1 = scaleMapper.selectMyById(scaleId);
            if(scale1.deleteFlat()){
                throw new BusinessException(ScaleExceptionEnum.SCALE_DELETED_NOT_ENABLED);
            }
        }
        scaleMapper.updateById(scale);

        if(dto.getScaleCategoryId() != null) {
            // 如果指定了分类，则更新分类的使用数量(加一)
            scaleCategoryMapper.updateUserCount(dto.getScaleCategoryId(), 1);
        }

        log.info("量表修改成功，scaleId: {}", scaleId);
    }

    @Override
    @Transactional
    public void restoreScale(Long scaleId) {
        log.info("开始恢复量表，scaleId: {}", scaleId);

        // 检查量表是否存在且已删除
        Scale scale = scaleMapper.selectMyById(scaleId);
        if (scale == null) {
            log.error("量表不存在，scaleId: {}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }

        if (!scale.deleteFlat()) {
            log.warn("量表未被删除，无需恢复，scaleId: {}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_DELETED);
        }

        // 级联恢复：先恢复量表，再恢复题目，再恢复选项，最后恢复规则
        // 恢复量表主表
        scaleMapper.updateMyDeleteFlat(Scale.builder().id(scaleId).deleted(DeleteConstant.DELETE_FLAG_NO).build());

        // 批量恢复题目
        scaleQuestionMapper.retractDelete(scaleId, DeleteConstant.DELETE_FLAG_NO);

        // 批量恢复选项（通过题目关联）
        scaleOptionMapper.retractDelete(scaleId, DeleteConstant.DELETE_FLAG_NO);

        // 批量恢复结果规则
        scaleResultRuleMapper.retractDelete(scaleId, DeleteConstant.DELETE_FLAG_NO);

        log.info("量表恢复成功，scaleId: {}", scaleId);
    }

}
