package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.permission.PersonRole;

/**
 * 用户角色关联表(PersonRole)表数据库访问层
 *
 * @author makejava
 * @since 2025-12-21 15:52:37
 */
public interface PersonRoleMapper extends BaseMapper<PersonRole> {

    void saveUserRole(@Param("personId") Long personId, @Param("roleName") String roleName);
}

