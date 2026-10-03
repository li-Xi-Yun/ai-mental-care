package org.lixiyun.server.infrastructure.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 节点端点信息——返回给客户端的 WebSocket 路径描述
 *
 * <p>每个 OutputNode 通过 getSocketInfo() 声明自己需要暴露的端点，
 * 客户端根据返回的路径直接连接对应的 WebSocket 端点收发数据。</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NodeEndpoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 端点描述 */
    private String description;

    /** WebSocket 路径 */
    private String path;

}