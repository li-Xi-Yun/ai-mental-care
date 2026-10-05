package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.dto.admin.user.AdminQueryDTO;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.vo.admin.user.AdminVO;

/**
 * @author lixiyun
 * @since 2026-04-18 20:53
 */
public interface AdminMapper extends BaseMapper<Admin> {

    /**
     * 分页查询管理员VO（联表：角色、创建人用户名）
     *
     * @param page     分页对象
     * @param queryDTO 查询条件
     * @return 管理员VO分页结果
     */
    IPage<AdminVO> selectAdminPage(Page<AdminVO> page, @Param("queryDTO") AdminQueryDTO queryDTO);

    /**
     * 根据ID查询管理员VO（联表：角色、创建人用户名）
     *
     * @param id 管理员ID
     * @return 管理员VO
     */
    AdminVO selectAdminVOById(@Param("id") Long id);
}