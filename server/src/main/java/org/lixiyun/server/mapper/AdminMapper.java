package org.lixiyun.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.vo.admin.sysadmin.AdminDetailVO;
import org.lixiyun.pojo.vo.admin.sysadmin.AdminPageVO;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-18 20:53
 */
public interface AdminMapper extends BaseMapper<Admin> {

    /**
     * 分页查询管理员列表（包含删除状态查询）
     *
     * @param keyword 查询关键词
     * @param status 状态
     * @return 管理员列表
     */
    List<AdminPageVO> pageAdminListWithDeleted(@Param("keyword") String keyword,
                                               @Param("status") Integer status,
                                               @Param("deleted") Integer deleted);

    /**
     * 查询管理员详情（外联查获取创建人与更新人的名称）
     *
     * @param id 管理员ID
     * @return 管理员详情
     */
    AdminDetailVO getAdminDetailWithNames(@Param("id") Long id);

}