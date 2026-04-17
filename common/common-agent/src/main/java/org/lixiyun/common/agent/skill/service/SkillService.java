package org.lixiyun.common.agent.skill.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author lixiyun
 * @since 2026-04-16 20:35
 */
public interface SkillService {

    /**
     * 获取所有技能文件夹名称列表
     * @param pageNum 当前页码
     * @param pageSize 每页数量
     * @return 技能文件夹名称列表
     */
    List<String> getAllFolderName(Integer pageNum, Integer pageSize);

    /**
     * 获取指定文件夹下的所有文件名称列表
     * @param folderName 文件夹名称
     * @return 文件名称列表
     */
    List<String> getAllFileByFolderName(String folderName);

    /**
     * 查询指定文件的内容
     * @param folderName 文件夹名称
     * @param fileName 文件名
     * @return 文件内容字符串
     */
    String getFileContext(String folderName, String fileName);

    /**
     * 修改文件夹名称
     * @param oldFolderName 旧文件夹名称
     * @param newFolderName 新文件夹名称
     */
    void updateFolderName(String oldFolderName, String newFolderName);

    /**
     * 修改文件名称
     * @param folderName 文件夹名称
     * @param oldFileName 旧文件名
     * @param newFileName 新文件名
     */
    void updateFileName(String folderName, String oldFileName, String newFileName);

    /**
     * 修改文件内容
     * @param folderName 文件夹名称
     * @param fileName 文件名
     * @param fileContext 文件内容字符串
     */
    void updateFileContext(String folderName, String fileName, String fileContext);

    /**
     * 删除指定文件夹及其下所有文件
     * @param folderName 文件夹名称
     */
    void deleteFolder(String folderName);

    /**
     * 删除指定文件
     * @param folderName 文件夹名称
     * @param fileName 文件名
     */
    void deleteFile(String folderName, String fileName);

    /**
     * 新增文件夹
     * @param folderName 文件夹名称
     */
    void addFolder(String folderName);

    /**
     * 新增文件
     * @param folderName 文件夹名称
     * @param fileName 文件名
     * @param fileContext 文件内容字符串
     */
    void addFile(String folderName, String fileName, String fileContext);

    /**
     * 上传单个文件到指定文件夹
     * @param folderName 文件夹名称
     * @param file 文件数据
     */
    void uploadFile(String folderName, MultipartFile file);

    /**
     * 上传压缩文件夹，解压到技能目录下
     * @param file 压缩文件数据
     */
    void uploadFolder(MultipartFile file);
}
