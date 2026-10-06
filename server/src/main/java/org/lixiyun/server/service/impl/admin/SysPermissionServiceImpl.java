package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.admin.permission.SysPermissionDTO;
import org.lixiyun.pojo.entity.permission.Permission;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionDetailVO;
import org.lixiyun.pojo.vo.admin.permission.SysPermissionTreeVO;
import org.lixiyun.server.mapper.PermissionMapper;
import org.lixiyun.server.service.admin.SysPermissionService;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统权限服务实现类
 * <p>
 * 提供权限的增删改查、权限树查询等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final PermissionMapper permissionMapper;

    @Override
    public List<SysPermissionTreeVO> getPermissionTree() {
        log.debug("权限Service-开始查询权限树形列表");

        List<Permission> permissionList = permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getDeleted, DeleteConstant.DELETE_FLAG_NO));

        if (CollUtil.isEmpty(permissionList)) {
            log.debug("权限Service-权限列表为空");
            return CollUtil.newArrayList();
        }

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

        log.debug("权限Service-权限树形列表查询完成，分组数: {}", treeList.size());
        return treeList;
    }

    @Override
    public SysPermissionDetailVO getPermissionDetail(Long id) {
        log.debug("权限Service-开始查询权限详情，权限ID: {}", id);

        SysPermissionDetailVO detailVO = permissionMapper.getPermissionDetailWithNames(id);

        if (detailVO == null) {
            log.error("权限Service-权限不存在或已删除，权限ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.PERMISSION_NOT_FOUND);
        }

        log.debug("权限Service-权限详情查询完成，权限ID: {}", id);
        return detailVO;
    }

    @Override
    public void addPermission(SysPermissionDTO permissionDTO) {
        log.debug("权限Service-开始新增权限，权限标识符: {}", permissionDTO.getPerms());

        String perms = permissionDTO.getPerms();
        String name = permissionDTO.getName();
        String groupName = permissionDTO.getGroupName();
        String remark = permissionDTO.getRemark();

        Permission existPermission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .and(wrapper -> {
                    wrapper.eq(Permission::getPerms, perms)
                            .or()
                            .eq(Permission::getName, name);
                })
                .eq(Permission::getDeleted, DeleteConstant.DELETE_FLAG_NO));
        if (existPermission != null) {
            log.error("权限Service-权限标识符或名称已存在，权限标识符: {}", perms);
            throw new BusinessException(AuthenticationExceptionEnum.PERMISSION_EXIST);
        }

        Permission permission = Permission.builder()
                .perms(perms)
                .name(name)
                .groupName(groupName)
                .remark(remark)
                .status(Permission.STATUS_NORMAL)
                .build();

        int inserted = permissionMapper.insert(permission);
        if (inserted == 0) {
            log.error("权限Service-权限新增失败，权限标识符: {}", perms);
            throw new BusinessException(AuthenticationExceptionEnum.PERMISSION_ADD_FAILED);
        }

        log.debug("权限Service-权限新增成功，权限ID: {}", permission.getId());
    }

    @Override
    public void updatePermission(Long id, SysPermissionDTO permissionDTO) {
        log.debug("权限Service-开始修改权限，权限ID: {}, 权限标识符: {}", id, permissionDTO.getPerms());

        Permission existPermission = permissionMapper.selectById(id);
        if (existPermission == null || existPermission.getDeleted() == 1) {
            log.error("权限Service-权限不存在或已删除，权限ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.PERMISSION_NOT_FOUND);
        }

        String perms = permissionDTO.getPerms();
        String name = permissionDTO.getName();
        String groupName = permissionDTO.getGroupName();
        String remark = permissionDTO.getRemark();

        Permission duplicatePerms = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .ne(Permission::getId, id)
                .and(wrapper -> {
                    wrapper.eq(StringUtils.isNotBlank(perms), Permission::getPerms, perms)
                            .or()
                            .eq(StringUtils.isNotBlank(name), Permission::getName, name);
                })
                .eq(Permission::getDeleted, DeleteConstant.DELETE_FLAG_NO)
        );

        if (duplicatePerms != null) {
            log.error("权限Service-权限标识符或名称已存在，权限标识符: {}", perms);
            throw new BusinessException(AuthenticationExceptionEnum.PERMISSION_EXIST);
        }

        Permission permission = Permission.builder()
                .id(id)
                .perms(perms)
                .name(name)
                .groupName(groupName)
                .remark(remark)
                .build();

        int updated = permissionMapper.updateById(permission);
        if (updated == 0) {
            log.error("权限Service-权限修改失败，权限ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.PERMISSION_UPDATE_FAILED);
        }

        log.debug("权限Service-权限修改成功，权限ID: {}", id);
    }

    @Override
    public void deletePermission(Long id) {
        log.debug("权限Service-开始删除权限，权限ID: {}", id);

        int deleted = permissionMapper.deleteById(id);
        if (deleted == 0) {
            log.error("权限Service-权限删除失败，权限ID: {}", id);
            throw new BusinessException(AuthenticationExceptionEnum.PERMISSION_DELETE_FAILED);
        }

        log.debug("权限Service-权限删除成功，权限ID: {}", id);
    }
}