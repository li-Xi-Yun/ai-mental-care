package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.permission.Permission;

import java.util.List;

/**
 * 权限表：存储菜单项及其对应权限标识(Permission)表数据库访问层
 *
 * @author makejava
 * @since 2025-12-21 15:52:37
 */
public interface PermissionMapper extends BaseMapper<Permission> {

    List<String> queryPermsByPersonId(@Param("personId") Long personId);
}

