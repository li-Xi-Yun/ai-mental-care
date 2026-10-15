package org.lixiyun.server.service.admin;

import org.lixiyun.common.sql.core.result.PageResult;
import org.lixiyun.pojo.dto.admin.config.Live2dConfigDTO;
import org.lixiyun.pojo.dto.admin.config.Live2dConfigQueryDTO;
import org.lixiyun.pojo.vo.admin.config.Live2dConfigAdminVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理员 Live2D 模型配置管理服务接口
 * <p>负责模型配置的写操作：上传、修改、删除、启用互斥切换与分页查询。</p>
 *
 * @author lixiyun
 * @since 2026-10-14
 */
public interface AdminLive2dConfigService {

    /**
     * 上传模型包（zip）并新增配置
     * <p>主要流程：① 安全解压至模型根目录/{modelName}/；② 一次遍历收集文件、解析 exp3/motion3 提取白名单；
     * ③ 落库（enabled=0，coverImagePath/semanticPrompt 未传一律为 null，由管理员编辑时手动录入）。</p>
     *
     * @param file               模型包 zip
     * @param modelName          模型名称（即解压目录名）
     * @param expressionFileName 表情清单文件名（可选；缺省按默认后缀 *.exp3.json 模糊匹配扫描）
     * @param motionFileName     动作清单文件名（可选；缺省按默认后缀 *.motion3.json 模糊匹配扫描）
     * @param semanticPrompt     语义提示词（可选；未传则落库 null，管理员编辑时手动录入）
     * @param sort               排序值（可选；未传默认 0）
     * @param remark             备注（可选）
     * @return 新增记录ID
     */
    Long uploadModel(MultipartFile file, String modelName, String expressionFileName, String motionFileName,
                     String semanticPrompt, Integer sort, String remark);

    /**
     * 更新模型配置
     *
     * @param id  记录ID
     * @param dto 更新请求
     */
    void update(Long id, Live2dConfigDTO dto);

    /**
     * 逻辑删除模型配置
     *
     * @param id 记录ID
     */
    void delete(Long id);

    /**
     * 启用/禁用模型（置 1 时自动将其他模型置 0，保证全局唯一启用）
     *
     * @param id      记录ID
     * @param enabled 是否启用：0-禁用 1-启用
     */
    void enable(Long id, Integer enabled);

    /**
     * 分页查询模型配置（管理端列表）
     *
     * @param queryDTO 分页查询请求
     * @return 分页结果
     */
    PageResult<Live2dConfigAdminVO> page(Live2dConfigQueryDTO queryDTO);
}