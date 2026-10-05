package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.admin.RegisterAdminDTO;
import org.lixiyun.pojo.dto.admin.user.AdminQueryDTO;
import org.lixiyun.pojo.dto.admin.user.AdminStatusDTO;
import org.lixiyun.pojo.dto.admin.user.AdminUpdateDTO;
import org.lixiyun.pojo.vo.admin.user.AdminVO;

/**
 * 管理员管理服务接口
 * <p>
 * 提供管理员的新增、分页查询、详情、编辑（含角色调整）、状态修改、注销等功能，
 * 该模块为超级管理员专属操作
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminManageService {

    /**
     * 新增管理员
     * <p>账号名由系统自动生成，绑定普通管理员角色</p>
     *
     * @param registerAdminDTO 新增管理员信息 {@link RegisterAdminDTO}
     */
    void registerAdmin(RegisterAdminDTO registerAdminDTO);

    /**
     * 分页查询所有管理员信息
     * <p>支持账号名、用户名、手机号模糊匹配和账号状态筛选，返回角色与创建人姓名</p>
     *
     * @param queryDTO 查询条件 {@link AdminQueryDTO}
     * @return 分页结果 {@link PageResult}
     */
    PageResult<AdminVO> listAdmins(AdminQueryDTO queryDTO);

    /**
     * 获取管理员详情
     * <p>根据管理员ID获取管理员详细信息（含角色与创建人），用于编辑弹窗回显</p>
     *
     * @param id 管理员ID
     * @return 管理员信息 {@link AdminVO}
     */
    AdminVO getAdminDetail(Long id);

    /**
     * 编辑管理员 / 调整角色
     * <p>更新管理员基本信息，支持账号名变更与角色调整（仅超级管理员可分配SUPER_ADMIN）</p>
     *
     * @param id         管理员ID
     * @param updateDTO  编辑信息 {@link AdminUpdateDTO}
     */
    void updateAdmin(Long id, AdminUpdateDTO updateDTO);

    /**
     * 修改管理员账号状态
     * <p>支持封禁并填写封禁理由，恢复/异常/注销时清空封禁字段</p>
     *
     * @param statusDTO 状态修改DTO {@link AdminStatusDTO}
     */
    void updateAdminStatus(AdminStatusDTO statusDTO);

    /**
     * 管理员注销
     * <p>将管理员账号状态置为注销(status=3)，并清空封禁字段，不能注销自己</p>
     *
     * @param id 管理员ID
     */
    void deleteAdmin(Long id);
}