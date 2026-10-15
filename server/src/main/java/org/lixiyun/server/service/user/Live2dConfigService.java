package org.lixiyun.server.service.user;

import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.vo.user.live2d.Live2dConfigVO;
import org.lixiyun.server.infrastructure.live2d.ModelFileSigner;

import java.util.List;

/**
 * 用户端 Live2D 模型配置服务接口
 * <p>负责模型配置的读操作与模型文件认证下载（通过 {@link ModelFileSigner} 签名校验后返回）。</p>
 *
 * @author lixiyun
 * @since 2026-10-14
 */
public interface Live2dConfigService {

    /**
     * 获取当前启用的模型配置（用户端）
     *
     * @return 启用模型 VO，无启用模型时返回 null
     */
    Live2dConfigVO getEnabledConfig();

    /**
     * 获取全部可用（未删除）模型列表（用户端）
     *
     * @return 模型列表
     */
    List<Live2dConfigVO> listAvailable();

    /**
     * 认证下载模型文件
     * <p>校验签名（HMAC + 有效期）与路径（必须位于 live2d-models/ 下防穿越）后返回文件字节；
     * model3.json 会实时改写内部 FileReferences 相对路径为带签名的完整下载 URL。</p>
     *
     * @param path    服务端相对路径（如 live2d-models/hiyori/hiyori.model3.json）
     * @param expires 签名过期时间戳（Unix 秒）
     * @param sign    签名
     * @return 文件字节与元信息
     * @throws BusinessException 路径越权或签名无效时抛出
     */
    ModelFileSigner.ModelFileBytes downloadModelFile(String path, long expires, String sign);
}