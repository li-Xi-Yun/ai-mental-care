package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.constant.DeleteConstant;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.vo.user.scale.ScaleVO;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.service.user.ScaleService;
import org.springframework.stereotype.Service;

/**
 * @author lixiyun
 * @since 2026-04-15 09:24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleServiceImpl implements ScaleService {

    private final ScaleMapper scaleMapper;

    @Override
    public PageResult<ScaleVO> listScales(Integer pageNum, Integer pageSize, Long scaleCategoryId) {
        Page<Scale> build = new PageQuery(pageSize, pageNum).build();
        Page<Scale> scalePage;
        if(scaleCategoryId != null){
            scalePage = scaleMapper.selectPage(build, new LambdaQueryWrapper<Scale>()
                    .eq(Scale::getScaleCategoryId, scaleCategoryId)
                    .eq(Scale::getStatus, Scale.STATUS_ENABLE)
                    .eq(Scale::getDeleted, DeleteConstant.DELETE_FLAG_NO));
        }else{
            scalePage = scaleMapper.selectPage(build, new LambdaQueryWrapper<Scale>()
                    .eq(Scale::getStatus, Scale.STATUS_ENABLE)
                    .eq(Scale::getDeleted, DeleteConstant.DELETE_FLAG_NO));
        }
        return PageResult.convert(scalePage, ScaleVO.class);
    }
}
