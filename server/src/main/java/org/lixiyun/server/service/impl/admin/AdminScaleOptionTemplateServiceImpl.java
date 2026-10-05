package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateApplyDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateCopyDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleOptionTemplateItemDTO;
import org.lixiyun.pojo.entity.scale.ScaleOption;
import org.lixiyun.pojo.entity.scale.ScaleOptionTemplateGroup;
import org.lixiyun.pojo.entity.scale.ScaleOptionTemplateItem;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleOptionTemplateItemVO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleOptionTemplateVO;
import org.lixiyun.server.mapper.ScaleOptionMapper;
import org.lixiyun.server.mapper.ScaleOptionTemplateGroupMapper;
import org.lixiyun.server.mapper.ScaleOptionTemplateItemMapper;
import org.lixiyun.server.service.admin.AdminScaleOptionTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 管理员量表选项模板服务实现类
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminScaleOptionTemplateServiceImpl implements AdminScaleOptionTemplateService {

    private final ScaleOptionTemplateGroupMapper scaleOptionTemplateGroupMapper;
    private final ScaleOptionTemplateItemMapper scaleOptionTemplateItemMapper;
    private final ScaleOptionMapper scaleOptionMapper;

    @Override
    public List<AdminScaleOptionTemplateVO> listTemplates(Long scaleVersionId) {
        log.info("查询版本选项模板列表，版本ID：{}", scaleVersionId);
        List<ScaleOptionTemplateGroup> groups = scaleOptionTemplateGroupMapper.selectList(new LambdaQueryWrapper<ScaleOptionTemplateGroup>()
                .eq(ScaleOptionTemplateGroup::getScaleVersionId, scaleVersionId));
        if (CollUtil.isEmpty(groups)) {
            return Collections.emptyList();
        }
        List<Long> groupIds = StreamUtils.toList(groups, ScaleOptionTemplateGroup::getId);
        List<ScaleOptionTemplateItem> items = scaleOptionTemplateItemMapper.selectList(new LambdaQueryWrapper<ScaleOptionTemplateItem>()
                .in(ScaleOptionTemplateItem::getTemplateGroupId, groupIds));
        Map<Long, List<ScaleOptionTemplateItem>> itemMap = StreamUtils.groupByKey(items, ScaleOptionTemplateItem::getTemplateGroupId);
        return StreamUtils.toList(groups, group -> {
            AdminScaleOptionTemplateVO vo = BeanUtil.copyProperties(group, AdminScaleOptionTemplateVO.class);
            vo.setDeletedFlag(group.getDeleted());
            List<ScaleOptionTemplateItem> groupItems = itemMap.get(group.getId());
            if (CollUtil.isNotEmpty(groupItems)) {
                vo.setItems(StreamUtils.toListVO(groupItems, AdminScaleOptionTemplateItemVO.class));
            }
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTemplate(AdminScaleOptionTemplateDTO dto) {
        log.info("新增选项模板组，版本ID：{}，参数：{}", dto.getScaleVersionId(), dto.getTemplateName());
        ScaleOptionTemplateGroup group = BeanUtil.copyProperties(dto, ScaleOptionTemplateGroup.class);
        group.setId(null);
        int row = scaleOptionTemplateGroupMapper.insert(group);
        if (row == 0) {
            log.error("选项模板组新增失败：{}", dto.getTemplateName());
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_TEMPLATE_ADD_FAIL);
        }
        List<AdminScaleOptionTemplateItemDTO> items = dto.getItems();
        if (CollUtil.isNotEmpty(items)) {
            Db.saveBatch(buildItems(items, group.getId()));
        }
        log.info("选项模板组新增成功，模板组ID：{}", group.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(Long groupId, AdminScaleOptionTemplateDTO dto) {
        log.info("修改选项模板组，模板组ID：{}，参数：{}", groupId, dto.getTemplateName());
        ScaleOptionTemplateGroup group = BeanUtil.copyProperties(dto, ScaleOptionTemplateGroup.class);
        group.setId(groupId);
        scaleOptionTemplateGroupMapper.updateById(group);
        List<AdminScaleOptionTemplateItemDTO> items = dto.getItems();
        if (items != null) {
            scaleOptionTemplateItemMapper.delete(new LambdaQueryWrapper<ScaleOptionTemplateItem>()
                    .eq(ScaleOptionTemplateItem::getTemplateGroupId, groupId));
            if (CollUtil.isNotEmpty(items)) {
                Db.saveBatch(buildItems(items, groupId));
            }
        }
        log.info("选项模板组修改成功，模板组ID：{}", groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyTemplate(Long groupId, AdminScaleOptionTemplateApplyDTO dto) {
        log.info("应用选项模板到题目，模板组ID：{}，参数：{}", groupId, dto.getQuestionIds());
        ScaleOptionTemplateGroup group = scaleOptionTemplateGroupMapper.selectById(groupId);
        if (group == null) {
            log.warn("选项模板组不存在，模板组ID：{}", groupId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_TEMPLATE_GROUP_NOT_FOUND);
        }
        List<ScaleOptionTemplateItem> items = scaleOptionTemplateItemMapper.selectList(new LambdaQueryWrapper<ScaleOptionTemplateItem>()
                .eq(ScaleOptionTemplateItem::getTemplateGroupId, groupId)
                .orderByAsc(ScaleOptionTemplateItem::getSort));
        if (CollUtil.isEmpty(items)) {
            log.warn("选项模板组下无明细，模板组ID：{}", groupId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_TEMPLATE_GROUP_NOT_FOUND);
        }
        String mode = StrUtil.isBlank(dto.getMode()) ? "REPLACE" : dto.getMode();
        List<Long> questionIds = dto.getQuestionIds();
        try {
            List<ScaleOption> newOptions = new ArrayList<>();
            for (Long questionId : questionIds) {
                if ("REPLACE".equalsIgnoreCase(mode)) {
                    scaleOptionMapper.delete(new LambdaQueryWrapper<ScaleOption>().eq(ScaleOption::getQuestionId, questionId));
                }
                for (ScaleOptionTemplateItem item : items) {
                    ScaleOption option = new ScaleOption();
                    option.setQuestionId(questionId);
                    option.setOptionText(item.getOptionText());
                    option.setScore(item.getScore());
                    option.setSort(item.getSort());
                    newOptions.add(option);
                }
            }
            Db.saveBatch(newOptions);
        } catch (Exception e) {
            log.error("模板应用到题目失败，模板组ID：{}", groupId, e);
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_TEMPLATE_APPLY_FAIL);
        }
        log.info("选项模板应用成功，模板组ID：{}，题目数：{}", groupId, questionIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyTemplate(AdminScaleOptionTemplateCopyDTO dto) {
        log.info("复制选项模板，模板组ID：{}，目标版本ID：{}", dto.getGroupId(), dto.getTargetScaleVersionId());
        Long groupId = dto.getGroupId();
        Long targetScaleVersionId = dto.getTargetScaleVersionId();
        ScaleOptionTemplateGroup sourceGroup = scaleOptionTemplateGroupMapper.selectById(groupId);
        if (sourceGroup == null) {
            log.warn("选项模板组不存在，模板组ID：{}", groupId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_OPTION_TEMPLATE_GROUP_NOT_FOUND);
        }
        ScaleOptionTemplateGroup newGroup = BeanUtil.copyProperties(sourceGroup, ScaleOptionTemplateGroup.class);
        newGroup.setId(null);
        newGroup.setScaleVersionId(targetScaleVersionId);
        scaleOptionTemplateGroupMapper.insert(newGroup);
        List<ScaleOptionTemplateItem> items = scaleOptionTemplateItemMapper.selectList(new LambdaQueryWrapper<ScaleOptionTemplateItem>()
                .eq(ScaleOptionTemplateItem::getTemplateGroupId, groupId)
                .orderByAsc(ScaleOptionTemplateItem::getSort));
        if (CollUtil.isNotEmpty(items)) {
            Db.saveBatch(StreamUtils.toList(items, item -> {
                ScaleOptionTemplateItem newItem = BeanUtil.copyProperties(item, ScaleOptionTemplateItem.class);
                newItem.setId(null);
                newItem.setTemplateGroupId(newGroup.getId());
                return newItem;
            }));
        }
        log.info("选项模板复制成功，模板组ID：{}，新模板组ID：{}", groupId, newGroup.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long groupId) {
        log.info("删除选项模板组，模板组ID：{}", groupId);
        scaleOptionTemplateItemMapper.delete(new LambdaQueryWrapper<ScaleOptionTemplateItem>()
                .eq(ScaleOptionTemplateItem::getTemplateGroupId, groupId));
        scaleOptionTemplateGroupMapper.deleteById(groupId);
        log.info("选项模板组删除成功，模板组ID：{}", groupId);
    }

    /**
     * 将模板项DTO列表转换为模板项实体列表并绑定模板组ID
     *
     * @param items      模板项DTO列表
     * @param templateGroupId 模板组ID
     * @return 模板项实体列表
     */
    private List<ScaleOptionTemplateItem> buildItems(List<AdminScaleOptionTemplateItemDTO> items, Long templateGroupId) {
        return StreamUtils.toList(items, item -> {
            ScaleOptionTemplateItem entity = BeanUtil.copyProperties(item, ScaleOptionTemplateItem.class);
            entity.setTemplateGroupId(templateGroupId);
            return entity;
        });
    }
}
