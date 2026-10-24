package org.lixiyun.server.socket.constant;

/**
 * 文件向量状态 WebSocket 推送常量
 * <p>服务端子路径传 {@link #FILE_VECTOR_STATUS}/{fileId}，前端订阅 {@code /user/queue/file/vector/status/{fileId}}。</p>
 *
 * @author lixiyun
 */
public interface FileVectorConstant {

    /** 文件向量解析状态推送子路径 */
    String FILE_VECTOR_STATUS = "/file/vector/status";
}