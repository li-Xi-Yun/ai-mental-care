package org.lixiyun.server.service.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.utils.StreamUtils;
import org.lixiyun.pojo.entity.Admin;
import org.lixiyun.pojo.entity.User;
import org.lixiyun.server.mapper.AdminMapper;
import org.lixiyun.server.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人员姓名解析助手
 * <p>后端各表均只保存 personnelId（人员ID），不冗余姓名字段。由于管理员与用户共用同一 ID 空间
 * （各自主键分别为 admin.id / user.id），解析姓名时优先查管理员表，未命中再查用户表。</p>
 *
 * @author lixiyun
 * @since 2026-10-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonNameHelper {

    private final AdminMapper adminMapper;
    private final UserMapper userMapper;

    /**
     * 解析单个人员ID对应的姓名
     * <p>优先匹配管理员表，其次匹配用户表；两者都未命中返回 null。</p>
     *
     * @param personId 人员ID，可为空
     * @return 人员姓名，未找到返回 null
     */
    public String resolveName(Long personId) {
        if (personId == null) {
            return null;
        }
        Admin admin = adminMapper.selectById(personId);
        if (admin != null) {
            return admin.getUsername();
        }
        User user = userMapper.selectById(personId);
        if (user != null) {
            return user.getUsername();
        }
        log.debug("人员姓名解析未命中，personId：{}", personId);
        return null;
    }

    /**
     * 批量解析人员ID列表对应的姓名映射
     * <p>避免循环查库，先批量查管理员表，未命中的 ID 再批量查用户表。</p>
     *
     * @param personIds 人员ID集合，可为空
     * @return personId -> name 映射，未命中的 ID 不在结果中
     */
    public Map<Long, String> resolveNames(Collection<Long> personIds) {
        Map<Long, String> nameMap = MapUtil.newHashMap();
        if (CollUtil.isEmpty(personIds)) {
            return nameMap;
        }
        Set<Long> ids = personIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return nameMap;
        }

        // 1. 管理员表批量查询
        for (Admin admin : adminMapper.selectBatchIds(ids)) {
            nameMap.put(admin.getId(), admin.getUsername());
        }

        // 2. 剩余未命中的 ID 再查用户表
        List<Long> remainingIds = StreamUtils.toList(ids, id -> nameMap.containsKey(id) ? null : id);
        if (CollUtil.isNotEmpty(remainingIds)) {
            for (User user : userMapper.selectBatchIds(remainingIds)) {
                nameMap.put(user.getId(), user.getUsername());
            }
        }
        return nameMap;
    }
}