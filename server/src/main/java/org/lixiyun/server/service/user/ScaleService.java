package org.lixiyun.server.service.user;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.vo.user.scale.ScaleVO;

/**
 * @author lixiyun
 * @since 2026-04-15 09:22
 */
public interface ScaleService {

    /**
     * 检索量表的分页列表，可选择按类别过滤。
     *
     * @param pageNum         要检索的页码
     * @param pageSize        每页的项目数
     * @param scaleCategoryId 要按其过滤的量表类别的ID（可选）
     * @return 包含ScaleVO对象列表的PageResult
     */
    PageResult<ScaleVO> listScales(Integer pageNum, Integer pageSize, Long scaleCategoryId);

}
