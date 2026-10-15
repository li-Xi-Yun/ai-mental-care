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
     * 签发模型文件签名下载 URL（用户端）
     * <p>前端无法自行生成签名（密钥在服务端），加载入口 model3.json 前先调用本接口换取带签名与
     * 有效期的下载 URL。子资源（纹理/动作/表情等）加载时由 {@code file} 下载接口改写 model3 内部
     * 路径为签名 URL，无需前端逐一签发。</p>
     *
     * @param path 模型文件相对路径（如 live2d-models/hiyori/hiyori.model3.json）
     * @return 完整签名下载 URL（/user/live2d-model/file?path=...&expires=...&sign=...）
     * @throws BusinessException 路径越权时抛出
     */
    String signDownloadUrl(String path);

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