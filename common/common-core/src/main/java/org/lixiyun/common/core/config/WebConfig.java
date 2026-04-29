package org.lixiyun.common.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file-url.upload.images}")
    private String imageUploadPath;

    @Value("${file-url.upload.videos}")
    private String videoUploadPath;

    @Value("${file-url.upload.files}")
    private String fileUploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将文件系统中的上传目录映射到Web路径
        registry.addResourceHandler("/static-resources/images/**")  // 所有以/images/开头的URL请求都会被这个规则处理
                .addResourceLocations("file:" + imageUploadPath);
        // "file:"前缀告诉Spring这是文件系统路径而不是classpath路
        // imageUploadPath是从配置文件中读取的实际文件存储目录，比如./uploads/images/
        // 完整路径可能是：file:./uploads/images/

        registry.addResourceHandler("/static-resources/videos/**")
                .addResourceLocations("file:" + videoUploadPath);

        registry.addResourceHandler("/static-resources/files/**")
                .addResourceLocations("file:" + fileUploadPath);

        registry.addResourceHandler("/static-resources/**")
                .addResourceLocations("file:" + "static-resources/**");



//        // 静态资源映射：排除所有 /ws/ 开头的路径
//        registry.addResourceHandler("/**")
//                .addResourceLocations("classpath:/static/")
//                .resourceChain(true)
//                .addResolver(new PathResourceResolver() {
//                    @Override
//                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
//                        // 核心：跳过WebSocket路径
//                        if (resourcePath.startsWith("ws/")) {
//                            return null;
//                        }
//                        return super.getResource(resourcePath, location);
//                    }
//                });
    }
}