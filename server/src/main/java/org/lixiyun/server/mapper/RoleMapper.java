package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.Role;

import java.util.List;

/**
 * 角色表(Role)表数据库访问层
 *
 * @author makejava
 * @since 2025-12-21 15:52:37
 */
public interface RoleMapper extends BaseMapper<Role> {

    List<String> queryRoleByPersonId(@Param("personId") Long personId);
}

