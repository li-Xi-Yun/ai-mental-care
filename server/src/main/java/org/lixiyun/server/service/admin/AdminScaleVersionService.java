package org.lixiyun.server.service.admin;

import org.lixiyun.pojo.dto.admin.scale.AdminScaleVersionCopyDTO;
import org.lixiyun.pojo.dto.admin.scale.AdminScaleVersionDTO;
import org.lixiyun.pojo.vo.admin.scale.AdminScaleVersionVO;

import java.util.List;

/**
 * 管理员量表版本服务接口
 *
 * @author lixiyun
 * @since 2026-10-05
 */
public interface AdminScaleVersionService {

    /**
     * 查询量表下的版本列表（含题目/维度统计与是否当前生效）
     *
     * @param scaleId 量表ID
     * @return 版本列表
     */
    List<AdminScaleVersionVO> listVersions(Long scaleId);

    /**
     * 获取版本详情
     *
     * @param versionId 版本ID
     * @return 版本详情
     */
    AdminScaleVersionVO getVersionDetail(Long versionId);

    /**
     * 新增空版本
     *
     * @param dto 版本DTO {@link AdminScaleVersionDTO}
     */
    void createVersion(AdminScaleVersionDTO dto);

    /**
     * 从源版本整卷复制出新版本（维度/题目/选项/规则/常模等）
     *
     * @param dto 版本复制DTO {@link AdminScaleVersionCopyDTO}
     */
    void copyVersion(AdminScaleVersionCopyDTO dto);

    /**
     * 修改版本信息
     *
     * @param versionId 版本ID
     * @param dto       版本DTO {@link AdminScaleVersionDTO}
     */
    void updateVersion(Long versionId, AdminScaleVersionDTO dto);

    /**
     * 发布版本为当前生效版本（写入量表 current_version_id）
     *
     * @param versionId 版本ID
     */
    void publishVersion(Long versionId);

    /**
     * 逻辑删除版本（当前生效版本禁止删除）
     *
     * @param versionId 版本ID
     */
    void deleteVersion(Long versionId);

    /**
     * 恢复已删除的版本
     *
     * @param versionId 版本ID
     */
    void restoreVersion(Long versionId);
}