package org.lixiyun.server.service.user;

import org.lixiyun.pojo.vo.user.scale.ScaleCategoryVO;

import java.util.List;

/**
 * 量表类别前台服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface ScaleCategoryService {

    /**
     * 查询启用中的量表类别列表
     * <p>只返回未删除的类别，按 sort ASC、id ASC 排序；scaleCount 为该类别下「启用且未删除且有当前生效版本」的量表数量，由服务端聚合计算。</p>
     *
     * @return 量表类别 VO 列表
     */
    List<ScaleCategoryVO> listCategories();
}