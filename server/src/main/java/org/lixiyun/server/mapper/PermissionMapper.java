package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.permission.Permission;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionDetailVO;

import java.util.List;

/**
 * 权限表：存储菜单项及其对应权限标识(Permission)表数据库访问层
 *
 * @author makejava
 * @since 2025-12-21 15:52:37
 */
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 根据人员ID查询人员的所有权限标识符
     *
     * @param personId 人员ID
     * @return 人员的所有权限标识符列表
     */
    List<String> queryPermsByPersonId(@Param("personId") Long personId);

    /**
     * 查询权限详情（外联查获取创建人与更新人的名称）
     *
     * @param id 权限ID
     * @return 权限详情
     */
    SysPermissionDetailVO getPermissionDetailWithNames(@Param("id") Long id);
}