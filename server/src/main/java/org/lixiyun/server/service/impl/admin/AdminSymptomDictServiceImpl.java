package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.SymptomDictExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.symptom.AdminSymptomDictQueryDTO;
import org.lixiyun.pojo.dto.admin.symptom.SymptomDictDTO;
import org.lixiyun.pojo.entity.conversation.SymptomDict;
import org.lixiyun.pojo.vo.admin.symptom.SymptomDictOptionVO;
import org.lixiyun.pojo.vo.admin.symptom.SymptomDictVO;
import org.lixiyun.server.mapper.SymptomDictMapper;
import org.lixiyun.server.service.admin.AdminSymptomDictService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员症状词典服务实现类
 *
 * @author lixiyun
 * @since 2026-08-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSymptomDictServiceImpl implements AdminSymptomDictService {

    private final SymptomDictMapper symptomDictMapper;

    @Override
    public PageResult<SymptomDictVO> pageSymptomDict(AdminSymptomDictQueryDTO queryDTO) {
        String symptomTerm = queryDTO.getSymptomTerm();
        String symptomCategory = queryDTO.getSymptomCategory();
        Integer status = queryDTO.getStatus();
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();

        log.info("症状词典-分页查询，症状名称：{}，分类：{}，状态：{}", symptomTerm, symptomCategory, status);

        Page<SymptomDict> page = new PageQuery(pageSize, pageNum).build();
        Page<SymptomDict> result = symptomDictMapper.selectPage(page, new LambdaQueryWrapper<SymptomDict>()
                .like(symptomTerm != null, SymptomDict::getSymptomTerm, symptomTerm)
                .eq(symptomCategory != null, SymptomDict::getSymptomCategory, symptomCategory)
                .eq(status != null, SymptomDict::getStatus, status)
                .orderByDesc(SymptomDict::getCreatedTime)
        );

        log.info("症状词典-分页查询完成，总数：{}", result.getTotal());
        return PageResult.convert(result, SymptomDictVO.class);
    }

    @Override
    public SymptomDictVO getSymptomDictDetail(Long id) {
        log.info("症状词典-获取详情，id：{}", id);

        SymptomDict dict = symptomDictMapper.selectById(id);
        if (dict == null) {
            log.error("症状词典-记录不存在，id：{}", id);
            throw new BusinessException(SymptomDictExceptionEnum.SYMPTOM_DICT_NOT_FOUND);
        }
        return BeanUtil.copyProperties(dict, SymptomDictVO.class);
    }

    @Override
    public void createSymptomDict(SymptomDictDTO dto) {
        log.info("症状词典-新增，标准症状名称：{}", dto);

        SymptomDict dict = BeanUtil.copyProperties(dto, SymptomDict.class);

        int insert = symptomDictMapper.insert(dict);
        if (insert == 0) {
            log.error("症状词典-新增失败：{}", dto);
            throw new BusinessException(SymptomDictExceptionEnum.ADD_SYMPTOM_DICT_FAIL);
        }

        log.info("症状词典-新增成功，id：{}", dict.getId());
    }

    @Override
    public void updateSymptomDict(Long id, SymptomDictDTO dto) {
        log.info("症状词典-修改，id：{}", id);

        SymptomDict symptomDict = BeanUtil.copyProperties(dto, SymptomDict.class);
        symptomDict.setId(id);

        int row = symptomDictMapper.updateById(symptomDict);
        if (row == 0) {
            log.error("症状词典-更新失败：{}", id);
            throw new BusinessException(SymptomDictExceptionEnum.SYMPTOM_DICT_NOT_FOUND);
        }
        log.info("症状词典-修改成功，id：{}", id);
    }

    @Override
    public void updateSymptomDictStatus(Long id, Integer status) {
        log.info("症状词典-更新状态，id：{}，status：{}", id, status);

        int update = symptomDictMapper.update(null, new LambdaUpdateWrapper<SymptomDict>()
                .eq(SymptomDict::getId, id)
                .set(SymptomDict::getStatus, status));

        if(update == 0){
            log.error("症状词典-更新状态失败：{}", id);
            throw new BusinessException(SymptomDictExceptionEnum.SYMPTOM_DICT_NOT_FOUND);
        }

        log.info("症状词典-更新状态成功，id：{}", id);
    }

    @Override
    public void batchDeleteSymptomDict(List<Long> ids) {
        log.info("症状词典-批量删除，ids：{}", ids);

        int row = symptomDictMapper.deleteByIds(ids);
        if(row == 0){
            log.error("症状词典-批量删除失败：{}", ids);
            throw new BusinessException(SymptomDictExceptionEnum.SYMPTOM_DICT_NOT_FOUND);
        }

        log.info("症状词典-批量删除成功，删除数量：{}", ids.size());
    }

    @Override
    public List<SymptomDictOptionVO> getSymptomDictOptions() {
        log.info("症状词典-获取下拉选项");

        List<SymptomDict> enabledDict = symptomDictMapper.selectList(new LambdaQueryWrapper<SymptomDict>()
                        .eq(SymptomDict::getStatus, SymptomDict.STATUS_ENABLED)
        );

        List<SymptomDictOptionVO> list = new ArrayList<>();
        enabledDict.forEach(item -> list.add(new SymptomDictOptionVO(item.getSymptomCategory())));

        log.info("症状词典-获取下拉选项成功，分组数量：{}", list.size());
        return list;
    }

}