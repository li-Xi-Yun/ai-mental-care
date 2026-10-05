package org.lixiyun.server.service.impl.admin;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.server.mapper.AdminMapper;
import org.lixiyun.server.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 量表模块审计姓名解析助手
 * <p>创建人/更新人字段存储的是用户ID，后台 VO 需要展示姓名，
 * 统一通过该助手将用户/管理员表查询结果合并为「ID -> 用户名」映射。</p>
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScaleAuditNameHelper {

    private final AdminMapper adminMapper;
    private final UserMapper userMapper;

    /**
     * 批量解析用户ID对应的用户名（用户表 + 管理员表合并查询）
     *
     * @param ids 用户ID集合
     * @return ID -> 用户名 映射，未命中的 ID 不出现
     */
    public Map<Long, String> resolveNames(Collection<Long> ids) {
        Map<Long, String> nameMap = new HashMap<>();
        if (CollUtil.isEmpty(ids)) {
            return nameMap;
        }
        List<Long> distinctIds = ids.stream().distinct().toList();

        List<User> users = userMapper.selectBatchIds(distinctIds);
        if (CollUtil.isNotEmpty(users)) {
            users.forEach(u -> nameMap.putIfAbsent(u.getId(), u.getUsername()));
        }

        List<Admin> admins = adminMapper.selectBatchIds(distinctIds);
        if (CollUtil.isNotEmpty(admins)) {
            admins.forEach(a -> nameMap.putIfAbsent(a.getId(), a.getUsername()));
        }
        return nameMap;
    }

}
