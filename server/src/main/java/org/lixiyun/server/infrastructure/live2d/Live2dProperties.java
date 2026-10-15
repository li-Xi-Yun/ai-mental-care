package org.lixiyun.server.infrastructure.live2d;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Live2D 模型配置属性
 * <p>对应 application.yaml 的 live2d 配置节，经 @ConfigurationProperties 绑定。
 * 风格对齐 {@code FileUrlProperties}（同仓库 common-core/properties）。</p>
 *
 * <pre>{@code
 * live2d:
 *   model-root-path: live2d-models/
 *   file-sign:
 *     secret: ""
 *     expire-seconds: 300
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "live2d")
public class Live2dProperties {

    /**
     * 模型文件根目录（相对运行目录，也可为绝对路径）。
     * <p><b>不入库 static-resources 静态映射</b>——模型访问统一走签名下载接口，
     * 裸链 404，防止随便一个链接都能下载模型文件。</p>
     */
    private String modelRootPath = "live2d-models/";

    /**
     * 文件签名相关配置
     */
    private FileSign fileSign = new FileSign();

    /**
     * 文件签名（HMAC-SHA256）配置
     */
    @Data
    public static class FileSign {

        /**
         * 签名密钥。
         * <p>生产环境务必覆盖！为空时由 {@link ModelFileSigner} 兜底内置默认值并告警，
         * 但默认值属公开约定，会削弱签名安全性。</p>
         */
        private String secret = "";

        /**
         * 签名默认有效期（秒），决定一次会话内模型子资源 URL 的可用时长
         */
        private Long expireSeconds = 300L;
    }
}