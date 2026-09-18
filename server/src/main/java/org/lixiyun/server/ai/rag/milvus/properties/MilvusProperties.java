package org.lixiyun.server.ai.rag.milvus.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Milvus 配置属性
 * @author lixiyun
 * @since 2026-05-03 16:57
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.vectorstore.milvus")
public class MilvusProperties {

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 集合名称
     */
    private String collectionName;

    /**
     * 客户端连接配置（对应 yml 里的 client 节点）
     */
    private Client client;

    /**
     * 嵌套类：匹配 spring.ai.vectorstore.milvus.client 层级
     */
    @Data
    public static class Client {
        /**
         * 主机地址
         */
        private String host;
        /**
         * 端口
         */
        private int port;
        /**
         * 用户名
         */
        private String username;
        /**
         * 密码
         */
        private String password;
    }

    // ==================== Client 字段的便捷访问方法 ====================

    /**
     * 获取主机地址
     * @return 主机地址，如果 client 为 null 则返回 null
     */
    public String getHost() {
        return client != null ? client.getHost() : null;
    }

    /**
     * 获取端口
     * @return 端口号，如果 client 为 null 则返回 0
     */
    public int getPort() {
        return client != null ? client.getPort() : 0;
    }

    /**
     * 获取用户名
     * @return 用户名，如果 client 为 null 则返回 null
     */
    public String getUsername() {
        return client != null ? client.getUsername() : null;
    }

    /**
     * 获取密码
     * @return 密码，如果 client 为 null 则返回 null
     */
    public String getPassword() {
        return client != null ? client.getPassword() : null;
    }

    /**
     * 设置主机地址
     * @param host 主机地址
     */
    public void setHost(String host) {
        if (client == null) {
            client = new Client();
        }
        client.setHost(host);
    }

    /**
     * 设置端口
     * @param port 端口号
     */
    public void setPort(int port) {
        if (client == null) {
            client = new Client();
        }
        client.setPort(port);
    }

    /**
     * 设置用户名
     * @param username 用户名
     */
    public void setUsername(String username) {
        if (client == null) {
            client = new Client();
        }
        client.setUsername(username);
    }

    /**
     * 设置密码
     * @param password 密码
     */
    public void setPassword(String password) {
        if (client == null) {
            client = new Client();
        }
        client.setPassword(password);
    }
}
