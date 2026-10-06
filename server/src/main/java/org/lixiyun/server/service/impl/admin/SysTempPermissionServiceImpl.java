package org.lixiyun.server.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionGrantDTO;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionQueryDTO;
import org.lixiyun.pojo.entity.permission.Permission;
import org.lixiyun.pojo.entity.permission.PersonTempPermission;
import org.lixiyun.pojo.vo.admin.permission.SysTempPermissionPageVO;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;
import org.lixiyun.server.mapper.PermissionMapper;
import org.lixiyun.server.mapper.PersonTempPermissionMapper;
import org.lixiyun.server.service.admin.SysTempPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysTempPermissionServiceImpl implements SysTempPermissionService {

    private final PersonTempPermissionMapper personTempPermissionMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public PageResult<SysTempPermissionPageVO> pageUserTempPermissionPersons(SysTempPermissionQueryDTO queryDTO) {
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();
        Long personId = queryDTO.getPersonId();
        Integer status = queryDTO.getStatus();

        log.info("临时权限Service-分页查询用户临时权限记录参数: {}", queryDTO);
        PageHelper.startPage(pageNum, pageSize);
        List<SysTempPermissionPageVO> persons = personTempPermissionMapper.pageUserTempPermissionPersons(personId, status);

        PageInfo<SysTempPermissionPageVO> pageInfo = new PageInfo<>(persons);
        log.info("临时权限Service-分页查询用户临时权限记录成功: {}", pageInfo.getSize());
        return PageResult.convert(pageInfo, SysTempPermissionPageVO.class);
    }

    @Override
    public PageResult<SysTempPermissionPageVO> pageAdminTempPermissionPersons(SysTempPermissionQueryDTO queryDTO) {
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();
        Long personId = queryDTO.getPersonId();
        Integer status = queryDTO.getStatus();
        log.info("临时权限Service-分页查询管理员临时权限记录参数: {}", queryDTO);

        PageHelper.startPage(pageNum, pageSize);
        List<SysTempPermissionPageVO> persons = personTempPermissionMapper.pageAdminTempPermissionPersons(personId, status);

        PageInfo<SysTempPermissionPageVO> pageInfo = new PageInfo<>(persons);

        log.info("临时权限Service-分页查询管理员临时权限记录成功: {}", pageInfo.getSize());
        return PageResult.convert(pageInfo, SysTempPermissionPageVO.class);
    }

    @Override
    public List<TempPermissionVO> listUserTempPermissions(Long personId, Integer status) {
        log.info("临时权限Service-查询用户临时权限记录参数: personId={}, status={}", personId, status);
        return personTempPermissionMapper.listUserTempPermissionByPersonId(personId, status);
    }

    @Override
    public List<TempPermissionVO> listAdminTempPermissions(Long personId, Integer status) {
        log.info("临时权限Service-查询管理员临时权限记录参数: personId={}, status={}", personId, status);
        return personTempPermissionMapper.listAdminTempPermissionByPersonId(personId, status);
    }

    @Override
    @Transactional
    public void grantTempPermission(SysTempPermissionGrantDTO grantDTO) {
        Long personId = grantDTO.getPersonId();
        List<Long> permissionIdList = grantDTO.getPermissionIdList();
        LocalDateTime startTime = grantDTO.getStartTime();
        LocalDateTime expireTime = grantDTO.getExpireTime();
        String grantReason = grantDTO.getGrantReason();

        if (!expireTime.isAfter(startTime) || expireTime.isBefore(LocalDateTime.now())) {
            throw new BusinessException(AuthenticationExceptionEnum.TEMP_PERMISSION_GRANT_FAILED);
        }

        List<Permission> permissions = permissionMapper.selectByIds(permissionIdList);
        long validPermissionCount = permissions.stream()
                .filter(permission -> Objects.equals(permission.getStatus(), Permission.STATUS_NORMAL)
                        && permission.getDeleted() == DeleteConstant.DELETE_FLAG_NO)
                .map(Permission::getId)
                .distinct()
                .count();
        if (validPermissionCount != permissionIdList.stream().distinct().count()) {
            throw new BusinessException(AuthenticationExceptionEnum.TEMP_PERMISSION_GRANT_FAILED);
        }

        log.info("临时权限Service-开始授予临时权限，用户ID：{}，权限ID列表：{}，生效时间：{}，过期时间：{}",
                personId, permissionIdList, startTime, expireTime);

        Long currentUserId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        List<PersonTempPermission> tempPermissionList = permissionIdList.stream()
                .distinct()
                .map(permissionId -> PersonTempPermission.builder()
                        .personId(personId)
                        .permissionId(permissionId)
                        .startTime(startTime)
                        .expireTime(expireTime)
                        .status(PersonTempPermission.STATUS_VALID)
                        .grantReason(grantReason)
                        .grantUserId(currentUserId)
                        .build())
                .collect(Collectors.toList());

        personTempPermissionMapper.insert(tempPermissionList);

        JwtUtil.deleteJwtWithRedis(personId, JwtType.ADMIN);
        JwtUtil.deleteJwtWithRedis(personId, JwtType.USER);

        log.info("临时权限Service-临时权限授予成功，用户ID：{}，授予权限数：{}", personId, tempPermissionList.size());
    }

    @Override
    public void revokeTempPermission(Long id) {
        log.info("临时权限Service-开始手动作废临时权限，记录ID：{}", id);

        PersonTempPermission tempPermission = personTempPermissionMapper.selectById(id);

        if (tempPermission == null) {
            log.error("临时权限Service-临时权限记录不存在，记录ID：{}", id);
            throw new BusinessException(AuthenticationExceptionEnum.TEMP_PERMISSION_NOT_FOUND);
        }

        if (!tempPermission.isValid()) {
            log.warn("临时权限Service-临时权限已作废，记录ID：{}", id);
            throw new BusinessException(AuthenticationExceptionEnum.TEMP_PERMISSION_ALREADY_REVOKED);
        }

        int updated = personTempPermissionMapper.update(null, new LambdaUpdateWrapper<PersonTempPermission>()
                .eq(PersonTempPermission::getId, id)
                .set(PersonTempPermission::getStatus, PersonTempPermission.STATUS_MANUAL_INVALID));
        if (updated == 0) {
            log.error("临时权限Service-临时权限作废失败，记录ID：{}", id);
            throw new BusinessException(AuthenticationExceptionEnum.TEMP_PERMISSION_REVOKE_FAILED);
        }

        Long personId = tempPermission.getPersonId();
        JwtUtil.deleteJwtWithRedis(personId, JwtType.ADMIN);
        JwtUtil.deleteJwtWithRedis(personId, JwtType.USER);

        log.info("临时权限Service-临时权限作废成功，记录ID：{}", id);
    }

}