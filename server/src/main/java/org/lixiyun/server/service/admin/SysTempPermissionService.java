package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionGrantDTO;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionQueryDTO;
import org.lixiyun.pojo.vo.admin.permission.SysTempPermissionPageVO;
import org.lixiyun.common.sql.core.result.PageResult;

/**
 * 系统临时权限服务接口
 * <p>
 * 提供临时权限的授予、作废、分页查询等功能
 * </p>
 *
 * @author lixiyun
 * @since 2026-07-31
 */
public interface SysTempPermissionService {

    /**
     * 分页查询临时权限记录
     * <p>
     * 支持用户ID和状态筛选，用于审计页面查看所有用户的临时授权记录
     * </p>
     *
     * @param queryDTO 查询条件 {@link SysTempPermissionQueryDTO}
     * @return 分页结果 {@link PageResult}
     */
    PageResult<SysTempPermissionPageVO> pageTempPermissionList(SysTempPermissionQueryDTO queryDTO);

    /**
     * 授予临时权限
     * <p>
     * 批量插入person_temp_permission表，不修改用户角色
     * </p>
     *
     * @param grantDTO 授予信息 {@link SysTempPermissionGrantDTO}
     */
    void grantTempPermission(SysTempPermissionGrantDTO grantDTO);

    /**
     * 手动作废临时权限
     * <p>
     * 将status设置为1（手动作废），不删除记录，保留审计信息
     * </p>
     *
     * @param id 临时权限记录ID
     */
    void revokeTempPermission(Long id);

}