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

    // 嵌套类匹配配置中的 upload 节点
    private Upload upload = new Upload();
    // 嵌套类匹配配置中的 request 节点
    private Request request = new Request();

    // 内部类：对应 upload 节点
    @Data
    public static class Upload {
        private String images;
        private String videos;
        private String audios;
        private String files;
        private String conversations;
        private String skills;
        private String prompts;
    }

    // 内部类：对应 request 节点
    @Data
    public static class Request {
        private String images;
        private String videos;
        private String audios;
        private String files;
        private String conversations;
        private String skills;
        private String prompts;
    }

    // 提供快捷获取方法（简化业务代码）
    public String getUploadImages() {
        return upload.getImages();
    }

    public String getRequestImages() {
        return request.getImages();
    }

    public String getUploadVideos() {
        return upload.getVideos();
    }

    public String getRequestVideos() {
        return request.getVideos();
    }

    public String getUploadAudio() {
        return upload.getAudios();
    }

    public String getRequestAudio() {
        return request.getAudios();
    }

    public String getUploadFiles() {
        return upload.getFiles();
    }

    public String getRequestFiles() {
        return request.getFiles();
    }

    public String getUploadConversations() {
        return upload.getConversations();
    }

    public String getRequestConversations() {
        return request.getConversations();
    }

    public String getUploadSkills() {
        return upload.getSkills();
    }

    public String getRequestSkills() {
        return request.getSkills();
    }

    public String getUploadPrompts() {
        return upload.getPrompts();
    }

    public String getRequestPrompts() {
        return request.getPrompts();
    }
}