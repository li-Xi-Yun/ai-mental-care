package org.lixiyun.server.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务接口
 */
public interface UploadService {

    /**
     * 图片上传
     * @param file 图片文件
     * @return 图片地址
     */
    String imageUpload(MultipartFile file);

}