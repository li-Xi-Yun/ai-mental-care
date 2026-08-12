package org.lixiyun.common.agent.skill.service;

import org.lixiyun.common.agent.skill.pojo.vo.SkillContentItemVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Skill服务接口
 * <p>提供Skill文件夹和文件的管理功能，包括查询、新增、修改、删除、下载等操作</p>
 *
 * @author lixiyun
 * @since 2026-04-16
 */
public interface SkillService {

    /**
     * 查询Skill文件夹下的所有内容
     * <p>
     * 实现流程：
     * 1. 判断Skill文件夹路径是否存在（不存在则查询根目录）
     * 2. 执行路径检查
     * 3. 查询该文件夹下所有文件名与文件夹名
     * 4. 为其中的Skill节点添加对应的相对路径信息
     * </p>
     *
     * @param skillFolderUrl              Skill文件夹路径（可选）
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @return 文件夹内容项列表 {@link SkillContentItemVO}
     */
    List<SkillContentItemVO> queryFolderContents(String skillFolderUrl, String supplementaryFolderUrl);

    /**
     * 查询指定文件的内容
     * <p>
     * 根据文件类型智能选择返回方式：
     * <ul>
     *     <li>文本文件（txt/md/json/xml/html等）：直接返回文本内容，响应体为 {@code Result<String>}</li>
     *     <li>非文本文件（图片/PDF/Word等）：返回文件数据流，响应体为 {@code ResponseEntity<Resource>}</li>
     * </ul>
     *
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @param fileName               文件名
     * @return 文本文件返回 {@code Result<String>}，非文本文件返回 {@code ResponseEntity<Resource>}
     */
    ResponseEntity<?> queryFileContent(String skillFolderUrl, String supplementaryFolderUrl, String fileName);

    /**
     * 新建文件夹
     * <p>
     * 实现流程：
     * 1. 路径检查
     * 2. 判断文件夹名称是否合法（不包含非法字符）
     * 3. 判断该文件夹下是否存在同名文件夹，存在则记录日志并报错
     * 4. 在该文件夹路径下创建新文件夹
     * </p>
     *
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @param folderName             文件夹名称
     */
    void createFolder(String skillFolderUrl, String supplementaryFolderUrl, String folderName);

    /**
     * 新建文件
     * <p>
     * 实现流程：
     * 1. 路径检查
     * 2. 判断文件名是否合法（不包含非法字符）
     * 3. 判断该文件夹下是否存在同名文件，存在则记录日志并报错
     * 4. 在该文件夹路径下创建空文件
     * </p>
     *
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @param fileName               文件名
     */
    void createFile(String skillFolderUrl, String supplementaryFolderUrl, String fileName);

    /**
     * 删除文件夹
     * <p>
     * 实现流程：
     * 1. 路径检查（验证路径安全性和存在性）
     * 2. 判断是否是Skill根目录（根目录不允许删除）
     * 3. 争夺Skill节点分布式锁（失败则报错）
     * 4. 获取现有影子表数据
     * 5. 遍历批量删除该节点及其所有子节点、孙节点的全部影子表数据
     * 6. 更新父节点的childrenKeys列表
     * 7. 接着重新加载或更新影子表到Redis
     * 8. 递归删除该文件夹及其所有子文件、子目录
     * 9. 释放分布式锁
     * </p>
     *
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     */
    void deleteFolder(String skillFolderUrl, String supplementaryFolderUrl);

    /**
     * 删除文件
     * <p>
     * 实现流程：
     * 1. 路径检查（验证路径安全性和存在性）
     * 2. 争夺Skill节点分布式锁（失败则报错）
     * 3. 判断是否是SKILL.md文件：
     *    - 否：直接删除文件
     *    - 是：删除文件并缓存清除SKILL在影子表中的value值
     * 4. 释放分布式锁
     * </p>
     *
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @param fileName               文件名
     */
    void deleteFile(String skillFolderUrl, String supplementaryFolderUrl, String fileName);

    /**
     * 上传单个文件
     * <p>
     * 实现流程：
     * 1. 路径检查（验证路径安全性和存在性）
     * 2. 判断上传文件是否超过指定大小（100MB），超过则记录日志并报错
     * 3. 判断目标文件夹下是否存在同名文件，存在则记录日志并报错
     * 4. 判断是否是SKILL.md文件：
     *    - 否：保存该文件到目标路径下
     *    - 是：解析Skill元数据格式 → 保存该文件到目标路径下 → 获取或创建全局影子表数据 → 更新父节点的childrenKeys列表 → 影子表新增或修改数据（递归处理子节点）→ 更新缓存中影子表
     * </p>
     *
     * @param file                   上传的文件 {@link MultipartFile}
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     */
    void uploadFile(MultipartFile file, String skillFolderUrl, String supplementaryFolderUrl);

    /**
     * 上传压缩包并解压为文件夹
     * <p>
     * 实现流程：
     * 1. 路径检查（验证路径安全性和存在性）
     * 2. 判断压缩包是否超过指定大小（100MB），超过则记录日志并报错
     * 3. 判断该文件夹下是否存在同名文件夹，存在则记录日志并报错
     * 4. 判断是否为空或是否有多个SKILL.md文件：
     *    - 遍历检查解压后的所有文件夹是否存在两个以上的SKILL.md文件，存在则记录日志并报错
     * 5. 解压到该文件夹路径下
     * 6. 判断是否已删除标识（结束）
     * 7. 获取或创建全局影子表数据
     * 8. 更新根节点中心节点的子节点列表
     * 9. 遍历生成或更新所有节点元数据到影子表中
     * 10. 更新缓存中影子表
     * </p>
     *
     * @param zipFile                压缩包文件 {@link MultipartFile}
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     */
    void uploadFolder(MultipartFile zipFile, String skillFolderUrl, String supplementaryFolderUrl);

    /**
     * 修改文件内容
     * <p>
     * 实现流程：
     * 1. 路径检查（验证路径安全性和存在性）
     * 2. 判断是否为纯文本文件（非文本文件不允许修改内容）
     * 3. 判断目标文件是否存在（不存在则报错）
     * 4. 争夺Skill节点分布式锁（失败则报错）
     * 5. 判断是否是SKILL.md文件：
     *    - 否：直接修改该文件内容
     *    - 是：解析新内容的元数据格式 → 获取或创建全局影子表数据 → 比较新旧元数据是否相同（相同则只修改文件）→ 更新影子表中该节点及子节点的元数据 → 缓存中影子表
     * 6. 释放分布式锁
     * </p>
     *
     * @param newContent             新的文件内容
     * @param fileName               文件名
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     */
    void updateFileContent(String newContent, String fileName, String skillFolderUrl, String supplementaryFolderUrl);

    /**
     * 修改文件夹名称
     * <p>
     * 实现流程：
     * 1. 路径检查（验证路径安全性和存在性）
     * 2. 验证新文件夹名称合法性（不包含非法字符）
     * 3. 判断新文件夹名下是否存在同名文件夹（存在则记录日志并报错）
     * 4. 争夺Skill节点分布式锁（失败则报错）
     * 5. 获取缓存影子表数据
     * 6. 根据文件夹路径参数从影子表获取该文件夹下的所有节点数据为一个Map
     * 7. 算法修改Map中的key，替换为新文件夹名的路径
     * 8. 同时修改所有节点中的父子节点索引列表
     * 9. 将新Map的数据更新到影子表中
     * 10. 修改物理文件夹名称
     * 11. 缓存新全局影子表数据
     * 12. 释放分布式锁
     * </p>
     *
     * @param newFolderName          新文件夹名称
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     */
    void renameFolder(String newFolderName, String skillFolderUrl, String supplementaryFolderUrl);

    /**
     * 修改文件名
     * <p>
     * 实现流程：
     * 1. 路径检查（验证路径安全性和存在性）
     * 2. 判断新文件名是否合法（不包含非法字符）
     * 3. 判断原路径是否是SKILL.md文件（是则记录日志并报错，SKILL.md不允许重命名）
     * 4. 判断该文件夹下是否存在同名文件（存在则记录日志并报错）
     * 5. 修改该文件名称
     * </p>
     *
     * @param newFileName            新文件名
     * @param originalFileName       原文件名
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     */
    void renameFile(String newFileName, String originalFileName, String skillFolderUrl, String supplementaryFolderUrl);

    /**
     * 下载文件夹为ZIP压缩包
     *
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @return ResponseEntity包含Resource资源
     */
    ResponseEntity<Resource> downloadFolderAsZip(String skillFolderUrl, String supplementaryFolderUrl);

    /**
     * 下载单个文件
     *
     * @param skillFolderUrl              Skill文件夹路径
     * @param supplementaryFolderUrl 补充文件夹路径（可选）
     * @param fileName               文件名
     * @return ResponseEntity包含Resource资源
     */
    ResponseEntity<Resource> downloadFile(String skillFolderUrl, String supplementaryFolderUrl, String fileName);
}