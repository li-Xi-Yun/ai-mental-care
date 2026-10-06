package org.lixiyun.server.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.authentication.enums.JwtType;
import org.lixiyun.common.authentication.utils.JwtUtil;
import org.lixiyun.pojo.entity.permission.PersonTempPermission;
import org.lixiyun.server.mapper.PersonTempPermissionMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 临时权限定时任务
 * <p>
 * 定时处理已过期的临时权限，将其状态更新为已过期
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31 17:24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TempPermissionTask {

    private final PersonTempPermissionMapper personTempPermissionMapper;

    /**
     * 处理已过期的临时权限
     * <p>
     * 每天凌晨2点执行，将person_temp_permission表中已过期且状态为有效的数据更新为已过期状态
     * </p>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void processExpiredPermissions() {
        log.info("定时任务-开始执行临时权限过期处理");

        List<PersonTempPermission> personTempPermissions = personTempPermissionMapper.selectList(new LambdaQueryWrapper<PersonTempPermission>()
                .eq(PersonTempPermission::getStatus, PersonTempPermission.STATUS_VALID)
                .lt(PersonTempPermission::getExpireTime, LocalDateTime.now()));

        if (personTempPermissions.isEmpty()) {
            log.info("定时任务-临时权限过期处理完成，没有过期的临时权限");
            return;
        }

        List<Long> tempPermissionIds = personTempPermissions.stream().map(PersonTempPermission::getId).collect(Collectors.toList());

        int updatedCount = personTempPermissionMapper.update(new LambdaUpdateWrapper<PersonTempPermission>()
                .in(PersonTempPermission::getId, tempPermissionIds)
                .set(PersonTempPermission::getStatus, PersonTempPermission.STATUS_EXPIRED)
        );

        tempPermissionIds.forEach(
                id -> {
                    JwtUtil.deleteJwtWithRedis(id, JwtType.ADMIN);
                    JwtUtil.deleteJwtWithRedis(id, JwtType.USER);
                }
        );

        log.info("定时任务-临时权限过期处理完成，更新记录数：{}", updatedCount);
    }
}