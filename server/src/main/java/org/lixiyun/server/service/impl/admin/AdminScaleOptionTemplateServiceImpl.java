package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.enums.SystemExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.admin.scale.ScaleOptionTemplateDTO;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleOptionTemplate;
import org.lixiyun.pojo.vo.admin.scale.ScaleOptionTemplateVO;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleOptionTemplateMapper;
import org.lixiyun.server.service.admin.AdminScaleOptionTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理员量表选项模板服务实现类
 *
 * @author lixiyun
 * @since 2026-04-20 21:40
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleOptionTemplateServiceImpl implements AdminScaleOptionTemplateService {

    private final ScaleOptionTemplateMapper scaleOptionTemplateMapper;
    private final ScaleMapper scaleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTemplate(List<ScaleOptionTemplateDTO> dtoList) {
        log.info("开始创建选项模板，数量: {}", dtoList.size());

        // 获取第一个DTO的量表ID进行验证（所有模板应该属于同一个量表）
        Long scaleId = dtoList.get(0).getScaleId();
        List<ScaleOptionTemplate> templates = dtoList.stream().map(item -> {
            if(!item.getScaleId().equals(scaleId)){
                log.error("创建选项模板-量表ID不一致，scaleId: {}, scaleId: {}", scaleId, item.getScaleId());
                throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
            }
            return BeanUtil.copyProperties(item, ScaleOptionTemplate.class);
        }).toList();

        // 验证量表是否存在
        Scale scale = scaleMapper.selectMyById(scaleId);
        if (scale == null) {
            log.error("创建选项模板-量表不存在，scaleId: {}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }

        // 批量插入选项模板
        scaleOptionTemplateMapper.insert(templates);

        log.info("选项模板创建成功，共创建 {} 条记录", templates.size());
    }

    @Override
    public List<ScaleOptionTemplateVO> listTemplates(Long scaleId) {
        log.debug("开始查询选项模板列表，scaleId: {}", scaleId);

        // 验证量表是否存在
        Scale scale = scaleMapper.selectMyById(scaleId);
        if (scale == null) {
            log.error("量表不存在，scaleId: {}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }

        // 查询该量表下的所有选项模板，按排序字段升序排列
        List<ScaleOptionTemplate> templates = scaleOptionTemplateMapper.selectList(
                new LambdaQueryWrapper<ScaleOptionTemplate>()
                        .eq(ScaleOptionTemplate::getScaleId, scaleId)
                        .eq(ScaleOptionTemplate::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                        .orderByAsc(ScaleOptionTemplate::getSort)
        );

        // 使用StreamUtils转换为VO列表
        List<ScaleOptionTemplateVO> voList = StreamUtils.toListVO(templates, ScaleOptionTemplateVO.class);

        log.debug("选项模板列表查询完成，共 {} 条记录", voList.size());
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(List<ScaleOptionTemplateDTO> dtoList) {
        log.info("开始更新选项模板，数量: {}", dtoList.size());

        // 获取第一个DTO的量表ID进行验证（所有模板应该属于同一个量表）
        Long scaleId = dtoList.get(0).getScaleId();
        List<ScaleOptionTemplate> updateTemplates = dtoList.stream().map(item -> {
            if(!item.getScaleId().equals(scaleId)){
                log.error("更新选项模板-量表ID不一致，scaleId: {}, scaleId: {}", scaleId, item.getScaleId());
                throw new BusinessException(SystemExceptionEnum.PARAM_ERROR);
            }
            return BeanUtil.copyProperties(item, ScaleOptionTemplate.class);
        }).toList();

        // 验证量表是否存在
        Scale scale = scaleMapper.selectMyById(scaleId);
        if (scale == null) {
            log.error("更新选项模板-量表不存在，scaleId: {}", scaleId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_NOT_FOUND);
        }

        // 批量更新选项模板
        scaleOptionTemplateMapper.updateById(updateTemplates);

        log.info("选项模板更新成功录");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long templateId) {
        log.info("开始删除选项模板，templateId: {}", templateId);

        // 查询模板是否存在
        ScaleOptionTemplate template = scaleOptionTemplateMapper.selectById(templateId);
        if (template == null) {
            log.error("选项模板不存在，templateId: {}", templateId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_NOT_FOUND);
        }

        // 逻辑删除
        int deleted = scaleOptionTemplateMapper.deleteById(templateId);
        if (deleted == 0) {
            log.error("选项模板删除失败，templateId: {}", templateId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_NOT_FOUND);
        }

        log.info("选项模板删除成功，templateId: {}", templateId);
    }
}
