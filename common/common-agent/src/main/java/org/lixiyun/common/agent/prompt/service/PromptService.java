package org.lixiyun.common.agent.prompt.service;

import java.util.List;

/**
 * Prompt服务接口
 * <p>提供提示词文件的管理功能，包括获取文件列表、查询文件内容、修改文件内容</p>
 * @author lixiyun
 * @since 2026-04-17
 */
public interface PromptService {

    /**
     * 获取所有提示词文件名
     * <p>遍历提示词文件夹，返回所有文件的名称（不含扩展名），按字母顺序排序</p>
     * @return 文件名列表
     */
    List<String> getAllFileNames();

    /**
     * 查询指定文件内容
     * <p>根据文件名查找对应的提示词文件，返回文件内容</p>
     * @param fileName 文件名（可带或不带扩展名）
     * @return 文件内容，如果文件不存在则返回空字符串
     */
    String getFileContent(String fileName);

    /**
     * 修改指定文件内容
     * <p>根据文件名修改提示词文件内容，并同步更新 {@link org.lixiyun.common.agent.prompt.utils.PromptUtil} 中的缓存</p>
     * @param fileName 文件名（可带或不带扩展名）
     * @param content 文件内容
     * @throws RuntimeException 当文件不存在时抛出
     */
    void updateFileContent(String fileName, String content);

}
