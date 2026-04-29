package org.lixiyun.common.agent.prompt.service;

import org.lixiyun.common.agent.prompt.dto.FolderContentItem;

import java.util.List;

/**
 * Prompt服务接口
 * <p>提供提示词文件的管理功能，包括获取文件列表、查询文件内容、修改文件内容</p>
 * @author lixiyun
 * @since 2026-04-17
 */
public interface PromptService {

    /**
     * 查询指定文件内容
     * <p>根据文件夹路径和文件名查找对应的提示词文件，返回文件内容</p>
     * @param folderUrl 文件夹路径（支持多级目录）
     * @param fileName 文件名（可带或不带扩展名）
     * @return 文件内容，如果文件不存在则返回空字符串
     */
    String getFileContent(String folderUrl, String fileName);

    /**
     * 修改指定文件内容
     * <p>根据文件夹路径和文件名修改提示词文件内容，并同步更新 {@link org.lixiyun.common.agent.prompt.utils.PromptUtil} 中的缓存</p>
     * @param folderUrl 文件夹路径（支持多级目录）
     * @param fileName 文件名（可带或不带扩展名）
     * @param content 文件内容
     * @throws RuntimeException 当文件不存在时抛出
     */
    void updateFileContent(String folderUrl, String fileName, String content);

    /**
     * 查询指定文件夹下的所有内容
     * <p>根据文件夹路径查询该文件夹下的所有文件和子文件夹名称，按字母顺序排序</p>
     * @param folderUrl 文件夹路径（支持多级目录，根目录传空字符串）
     * @return 文件夹内容项列表，包含名称和类型信息 {@link FolderContentItem}
     */
    List<FolderContentItem> getFolderContents(String folderUrl);

}
