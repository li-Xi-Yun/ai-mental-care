package org.lixiyun.common.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author lixiyun
 * @since 2025-12-23 12:47
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file-url")
public class FileUrlProperties {

    /**
     * 上传文件的URL
     */
    private String uploadImages;

    /**
     * 上传视频的URL
     */
    private String uploadVideos;

    /**
     * 上传文件的URL
     */
    private String uploadFiles;

    /**
     * 上传会话的URL
     */
    private String uploadConversations;

    /**
     * 请求图片的URL
     */
    private String requestImages;

    /**
     * 请求视频的URL
     */
    private String requestVideos;

    /**
     * 请求文件的URL
     */
    private String requestFiles;

    /**
     * 请求会话的URL
     */
    private String requestConversations;

}
