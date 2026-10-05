package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.permission.Role;
import org.lixiyun.pojo.vo.admin.permission.SysRoleDetailVO;

import java.util.List;

/**
 * 角色表(Role)表数据库访问层
 *
 * @author makejava
 * @since 2025-12-21 15:52:37
 */
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 查询用户角色键列表
     * @param personId 用户ID
     * @return 角色键列表
     */
    List<String> queryRoleKeysByPersonId(@Param("personId") Long personId);

    /**
     * 查询用户角色详情列表
     * @param personId 用户ID
     * @return 角色详情列表
     */
    List<Role> queryRoleDetailByPersonId(@Param("personId") Long personId);

    /**
     * 查询角色详情
     * @param id 角色ID
     * @return 角色详情
     */
    SysRoleDetailVO queryRoleDetailByRoleId(@Param("id") Long id);
}

