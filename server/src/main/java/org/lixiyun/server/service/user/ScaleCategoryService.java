package org.lixiyun.server.service.user;

import org.lixiyun.pojo.vo.user.scale.ScaleCategoryVO;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-15 14:22
 */
public interface ScaleCategoryService {

    /**
     * 检索所有量表分类的列表。
     *
     * @return 表示分类的ScaleCategoryVO对象列表
     */
    List<ScaleCategoryVO> listCategories();

}
