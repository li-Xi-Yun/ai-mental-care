package org.lixiyun.server.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionGrantDTO;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionQueryDTO;
import org.lixiyun.pojo.entity.permission.PersonTempPermission;
import org.lixiyun.pojo.vo.admin.permission.SysTempPermissionPageVO;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;
import org.lixiyun.server.mapper.PersonTempPermissionMapper;
import org.lixiyun.server.service.admin.SysTempPermissionService;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.sql.core.result.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统临时权限服务实现类
 * <p>
 * 提供临时权限的授予、作废、分页查询等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysTempPermissionServiceImpl implements SysTempPermissionService {

    private final PersonTempPermissionMapper personTempPermissionMapper;

    @Override
    public PageResult<SysTempPermissionPageVO> pageTempPermissionList(SysTempPermissionQueryDTO queryDTO) {
        Integer pageNum = queryDTO.getPageNum();
        Integer pageSize = queryDTO.getPageSize();
        Long personId = queryDTO.getPersonId();
        Integer status = queryDTO.getStatus();

        log.debug("临时权限Service-开始分页查询临时权限记录，页码：{}，每页数量：{}，用户ID：{}，状态：{}",
                pageNum, pageSize, personId, status);

        PageHelper.startPage(pageNum, pageSize);
        List<TempPermissionVO> resultPage = personTempPermissionMapper.pageTempPermissionList(personId, status);

        PageInfo<TempPermissionVO> pageInfo = new PageInfo<>(resultPage);

        Map<Long, List<TempPermissionVO>> collect = resultPage.stream()
                .collect(Collectors.groupingBy(TempPermissionVO::getPersonId));

        List<SysTempPermissionPageVO> result = collect.entrySet().stream().map(item -> SysTempPermissionPageVO.builder()
                .personId(item.getKey())
                .itemList(item.getValue())
                .build()).toList();

        log.debug("临时权限Service-查询完成，总记录数：{}，当前页记录数：{}",
                pageInfo.getTotal(), resultPage.size());

        return new PageResult<>(pageInfo.getTotal(), result);
    }

    @Override
    public void grantTempPermission(SysTempPermissionGrantDTO grantDTO) {
        Long personId = grantDTO.getPersonId();
        List<Long> permissionIdList = grantDTO.getPermissionIdList();
        LocalDateTime startTime = grantDTO.getStartTime();
        LocalDateTime expireTime = grantDTO.getExpireTime();
        String grantReason = grantDTO.getGrantReason();

        log.info("临时权限Service-开始授予临时权限，用户ID：{}，权限ID列表：{}，生效时间：{}，过期时间：{}",
                personId, permissionIdList, startTime, expireTime);

        Long currentUserId = UserInfoThreadLocalUtil.getCurrentIdThrow();

        List<PersonTempPermission> tempPermissionList = permissionIdList.stream()
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