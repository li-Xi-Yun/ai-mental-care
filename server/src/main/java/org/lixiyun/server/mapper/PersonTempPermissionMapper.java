package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.permission.PersonTempPermission;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;

import java.util.List;

/**
 * 用户临时权限表(PersonTempPermission)表数据库访问层
 *
 * @author lixiyun
 * @since 2026-07-30
 */
public interface PersonTempPermissionMapper extends BaseMapper<PersonTempPermission> {

    /**
     * 分页查询临时权限记录（多表联查）
     *
     * @param personId 用户ID
     * @param status 状态
     * @return 分页结果
     */
    List<TempPermissionVO> pageTempPermissionList(@Param("personId") Long personId,
                                                  @Param("status") Integer status);

}