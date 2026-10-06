package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionGrantDTO;
import org.lixiyun.pojo.dto.admin.permission.SysTempPermissionQueryDTO;
import org.lixiyun.pojo.vo.admin.permission.SysTempPermissionPageVO;
import org.lixiyun.pojo.vo.admin.permission.TempPermissionVO;

import java.util.List;

public interface SysTempPermissionService {

    PageResult<SysTempPermissionPageVO> pageUserTempPermissionPersons(SysTempPermissionQueryDTO queryDTO);

    PageResult<SysTempPermissionPageVO> pageAdminTempPermissionPersons(SysTempPermissionQueryDTO queryDTO);

    List<TempPermissionVO> listUserTempPermissions(Long personId, Integer status);

    List<TempPermissionVO> listAdminTempPermissions(Long personId, Integer status);

    void grantTempPermission(SysTempPermissionGrantDTO grantDTO);

    void revokeTempPermission(Long id);
}