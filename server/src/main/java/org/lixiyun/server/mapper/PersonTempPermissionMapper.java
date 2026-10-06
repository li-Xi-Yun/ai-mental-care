package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.permission.PersonTempPermission;
import org.lixiyun.pojo.vo.admin.permission.SysTempPermissionPageVO;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;

import java.util.List;

/**
 * 人员权限临时表Mapper
 * @author lixiyun
 * @date 2026/10/6
 */
public interface PersonTempPermissionMapper extends BaseMapper<PersonTempPermission> {

    /**
     * 分页查询拥有临时权限的用户人员列表（关联user表）
     *
     * @param personId 用户ID
     * @param status   用户账号状态，可选过滤条件
     * @return 临时权限人员分页列表
     */
    List<SysTempPermissionPageVO> pageUserTempPermissionPersons(@Param("personId") Long personId, @Param("status") Integer status);

    /**
     * 分页查询拥有临时权限的管理员人员列表（关联admin表）
     *
     * @param personId 管理员ID
     * @param status   管理员账号状态，可选过滤条件
     * @return 临时权限人员分页列表
     */
    List<SysTempPermissionPageVO> pageAdminTempPermissionPersons(@Param("personId") Long personId, @Param("status") Integer status);

    /**
     * 根据人员ID查询该用户的临时权限记录列表
     *
     * @param personId 用户ID
     * @param status   临时权限状态：0=有效，1=手动作废，2=已过期，可选过滤条件
     * @return 临时权限记录列表
     */
    List<TempPermissionVO> listUserTempPermissionByPersonId(@Param("personId") Long personId,
                                                             @Param("status") Integer status);

    /**
     * 根据人员ID查询该管理员的临时权限记录列表
     *
     * @param personId 管理员ID
     * @param status   临时权限状态：0=有效，1=手动作废，2=已过期，可选过滤条件
     * @return 临时权限记录列表
     */
    List<TempPermissionVO> listAdminTempPermissionByPersonId(@Param("personId") Long personId,
                                                              @Param("status") Integer status);
}