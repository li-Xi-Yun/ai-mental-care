package org.lixiyun.server.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.entity.scale.ScaleCategory;
import org.lixiyun.pojo.vo.user.scale.ScaleCategoryVO;
import org.lixiyun.server.mapper.ScaleCategoryMapper;
import org.lixiyun.server.service.user.ScaleCategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 14:22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScaleCategoryServiceImpl implements ScaleCategoryService {

    private final ScaleCategoryMapper scaleCategoryMapper;

    @Override
    public List<ScaleCategoryVO> listCategories() {
        List<ScaleCategory> categories = scaleCategoryMapper.selectList(new LambdaQueryWrapper<ScaleCategory>()
                .eq(ScaleCategory::getDeleted, DeleteConstant.DELETE_FLAG_NO));
        return StreamUtils.toListVO(categories, ScaleCategoryVO.class);
    }

}
