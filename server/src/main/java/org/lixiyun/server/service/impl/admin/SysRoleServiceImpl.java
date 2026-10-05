package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.admin.permission.SysRoleDTO;
import org.lixiyun.pojo.dto.admin.permission.SysRoleQueryDTO;
import org.lixiyun.pojo.entity.permission.Permission;
import org.lixiyun.pojo.entity.permission.PersonRole;
import org.lixiyun.pojo.entity.permission.Role;
import org.lixiyun.pojo.entity.permission.RolePermission;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionTreeVO;
import org.lixiyun.pojo.vo.admin.permission.SysRoleDetailVO;
import org.lixiyun.pojo.vo.admin.permission.SysRolePageVO;
import org.lixiyun.pojo.vo.admin.permission.SysRoleSimpleVO;
import org.lixiyun.server.mapper.PermissionMapper;
import org.lixiyun.server.mapper.PersonRoleMapper;
import org.lixiyun.server.mapper.RoleMapper;
import org.lixiyun.server.mapper.RolePermissionMapper;
import org.lixiyun.server.service.admin.SysRoleService;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.common.sql.core.page.PageQuery;
import org.lixiyun.common.sql.core.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统角色服务实现类
 * <p>
 * 提供角色的增删改查、状态管理、权限分配等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final RoleMapper roleMapper;
    private final PersonRoleMapper personRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public PageResult<SysRolePageVO> pageRoleList(SysRoleQueryDTO queryDTO) {
        log.debug("角色Service-开始分页查询角色列表，查询条件: {}", queryDTO);

        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();
        String name = queryDTO.getName();
        String roleKey = queryDTO.getRoleKey();
        Integer status = queryDTO.getStatus();

        Page<Role> build = new PageQuery(pageSize, pageNum).build();
        Page<Role> roleList = roleMapper.selectPage(build, new LambdaQueryWrapper<Role>()
                .like(StrUtil.isNotBlank(name), Role::getName, name)
                .eq(StrUtil.isNotBlank(roleKey), Role::getRoleKey, roleKey)
                .eq(status != null, Role::getStatus, status)
                .eq(Role::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByDesc(Role::getCreatedTime)
        );

        log.debug("角色Service-角色列表查询完成，总数: {}, 当前页记录数: {}", roleList.getTotal(), roleList.getRecords().size());
        return PageResult.convert(roleList, SysRolePageVO.class);
    }

    @Override
    public SysRoleDetailVO getRoleDetail(Long id) {
        log.debug("角色Service-开始查询角色详情，角色ID: {}", id);

        SysRoleDetailVO role = roleMapper.queryRoleDetailByRoleId(id);

        if (role == null) {
            log.error("角色Service-角色不存在或已删除，角色ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ROLE_NOT_FOUND);
        }

        log.debug("角色Service-角色详情查询完成，角色ID: {}", id);
        return role;
    }

    @Override
    public void addRole(SysRoleDTO roleDTO) {
        log.debug("角色Service-开始新增角色，角色标识: {}", roleDTO.getRoleKey());

        String roleKey = roleDTO.getRoleKey();
        String name = roleDTO.getName();
        String remark = roleDTO.getRemark();

        Role existRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .and(wrapper -> wrapper
                        .eq(Role::getRoleKey, roleKey)
                        .or()
                        .eq(Role::getName, name))
                .eq(Role::getDeleted, DeleteConstant.DELETE_FLAG_NO));

        if (existRole != null) {
            log.error("角色Service-角色名称或角色标识已存在，角色名称: {}，角色标识: {}", name, roleKey);
            throw new BusinessException(AuthenticationExceptionEnum.ROLE_EXIST);
        }

        Role role = Role.builder()
                .roleKey(roleKey)
                .name(name)
                .remark(remark)
                .status(Role.STATUS_NORMAL)
                .build();

        int inserted = roleMapper.insert(role);
        if (inserted == 0) {
            log.error("角色Service-角色新增失败，角色标识: {}", roleKey);
            throw new BusinessException(AuthenticationExceptionEnum.ROLE_ADD_FAILED);
        }

        log.info("角色Service-角色新增成功，角色ID: {}, 角色标识: {}", role.getId(), roleKey);
    }

    @Override
    public void updateRole(Long id, SysRoleDTO roleDTO) {
        log.debug("角色Service-开始修改角色信息，角色ID: {}, 角色标识: {}", id, roleDTO.getRoleKey());

        String roleKey = roleDTO.getRoleKey();
        String name = roleDTO.getName();
        String remark = roleDTO.getRemark();

        Role duplicateRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .and(wrapper -> wrapper
                        .eq(StrUtil.isNotBlank(roleKey), Role::getRoleKey, roleKey)
                        .or()
                        .eq(StrUtil.isNotBlank(name), Role::getName, name))
                .eq(Role::getDeleted, DeleteConstant.DELETE_FLAG_NO));

        if (duplicateRole != null) {
            log.error("角色Service-角色名称或角色标识已存在，角色名称: {}，角色标识: {}", name, roleKey);
            throw new BusinessException(AuthenticationExceptionEnum.ROLE_EXIST);
        }

        Role role = Role.builder()
                .id(id)
                .roleKey(roleKey)
                .name(name)
                .remark(remark)
                .build();

        int updated = roleMapper.updateById(role);
        if (updated == 0) {
            log.error("角色Service-角色信息修改失败，角色ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ROLE_UPDATE_FAILED);
        }

        log.info("角色Service-角色信息修改成功，角色ID: {}", id);
    }

    @Override
    public void updateRoleStatus(Long id, Integer status) {
        log.debug("角色Service-开始修改角色状态，角色ID: {}, 状态: {}", id, status);

        Role role = Role.builder()
                .id(id)
                .status(status)
                .build();

        int updated = roleMapper.updateById(role);
        if (updated == 0) {
            log.error("角色Service-角色状态修改失败，角色ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ROLE_STATUS_UPDATE_FAILED);
        }

        deleteJwt(id);

        log.info("角色Service-角色状态修改成功，角色ID: {}, 新状态: {}", id, status);
    }

    @Override
    public void deleteRole(Long id) {
        log.debug("角色Service-开始删除角色，角色ID: {}", id);

        int deleted = roleMapper.deleteById(id);
        if (deleted == 0) {
            log.error("角色Service-角色删除失败，角色ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ROLE_DELETE_FAILED);
        }

        deleteJwt(id);

        log.info("角色Service-角色删除成功，角色ID: {}", id);
    }

    @Override
    public List<SysPermissionTreeVO> getRolePermissions(Long id) {
        log.debug("角色Service-开始查询角色权限集合，角色ID: {}", id);

        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, id));

        List<Long> permissionIds = rolePermissions.stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toList());

        if (permissionIds.isEmpty()) {
            return List.of();
        }

        List<Permission> permissionList = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>()
                        .in(Permission::getId, permissionIds)
                        .eq(Permission::getDeleted, DeleteConstant.DELETE_FLAG_NO));

        Map<String, List<SysPermissionTreeVO.PermissionItem>> groupMap = permissionList.stream()
                .collect(Collectors.groupingBy(
                        Permission::getGroupName,
                        Collectors.mapping(
                                permission -> SysPermissionTreeVO.PermissionItem.builder()
                                        .id(permission.getId())
                                        .perms(permission.getPerms())
                                        .name(permission.getName())
                                        .status(permission.getStatus())
                                        .build(),
                                Collectors.toList()
                        )
                ));

        List<SysPermissionTreeVO> treeList = StreamUtils.toList(groupMap.entrySet(), entry ->
                SysPermissionTreeVO.builder()
                        .groupName(entry.getKey())
                        .permissions(entry.getValue())
                        .build()
        );


        log.debug("角色Service-角色权限集合查询完成，角色ID: {}, 权限数量: {}", id, permissionIds.size());
        return treeList;
    }

    @Override
    @Transactional
    public void assignPermissions(Long id, List<Long> permissionIds) {
        log.debug("角色Service-开始为角色分配权限，角色ID: {}, 权限ID数量: {}", id, permissionIds.size());

        List<RolePermission> list = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, id));

        List<Long> orderList = list.stream()
                .map(RolePermission::getPermissionId)
                .sorted()
                .toList();

        permissionIds = permissionIds.stream().sorted().toList();

        if (orderList.equals(permissionIds)) {
            return;
        }

        Role existRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getId, id)
                .eq(Role::getDeleted, DeleteConstant.DELETE_FLAG_NO));

        if (existRole == null) {
            log.error("角色Service-角色不存在或已删除，角色ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.ROLE_NOT_FOUND);
        }

        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, id));

        List<RolePermission> rolePermissions = permissionIds.stream()
                .map(permissionId -> RolePermission.builder()
                        .roleId(id)
                        .permissionId(permissionId)
                        .build())
                .collect(Collectors.toList());

        rolePermissionMapper.insert(rolePermissions);

        log.info("角色Service-角色权限分配成功，角色ID: {}, 权限数量: {}", id, permissionIds.size());
    }

    @Override
    public List<SysRoleSimpleVO> getAllSimpleRoles() {
        log.debug("角色Service-开始获取全部正常角色列表");

        List<Role> roleList = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getStatus, Role.STATUS_NORMAL)
                .eq(Role::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByAsc(Role::getId));

        List<SysRoleSimpleVO> simpleVOList = roleList.stream()
                .map(role -> SysRoleSimpleVO.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .build())
                .collect(Collectors.toList());

        log.debug("角色Service-正常角色列表查询完成，角色数量: {}", simpleVOList.size());
        return simpleVOList;
    }

    private void deleteJwt(Long id) {
        List<PersonRole> personRoles = personRoleMapper.selectList(new LambdaQueryWrapper<PersonRole>()
                .eq(PersonRole::getRoleId, id));

        personRoles.forEach(item -> {
            JwtUtil.deleteJwtWithRedis(item.getPersonId(), JwtType.ADMIN);
            JwtUtil.deleteJwtWithRedis(item.getPersonId(), JwtType.USER);
        });
    }

    @Override
    public boolean assignRoles(Long id, List<Long> roleIdList) {
        List<Role> currentRoles = roleMapper.queryRoleDetailByPersonId(id);
        List<Long> currentRoleIds = currentRoles.stream()
                .map(Role::getId)
                .sorted()
                .toList();

        List<Long> newRoleIds = roleIdList.stream()
                .sorted()
                .toList();

        if (currentRoleIds.equals(newRoleIds)) {
            log.debug("角色Service-角色列表未发生变化，人员ID: {}", id);
            return false;
        }

        int rows = personRoleMapper.delete(new LambdaQueryWrapper<PersonRole>().eq(PersonRole::getPersonId, id));
        if (rows == 0 || rows != currentRoleIds.size()) {
            return false;
        }

        List<PersonRole> personRoleList = roleIdList.stream()
                .map(roleId -> PersonRole.builder()
                        .personId(id)
                        .roleId(roleId)
                        .build())
                .collect(Collectors.toList());

        personRoleMapper.insert(personRoleList);
        return true;
    }

}