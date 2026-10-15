package org.lixiyun.server.infrastructure.live2d;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.Live2dConfigExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.core.utils.SignatureUtil;
import org.lixiyun.common.json.utils.JsonUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Live2D 模型文件签名访问器
 *
 * <p><b>职责</b>：作为模型文件唯一的认证访问入口的后端支撑——校验下载参数、将
 * {@code model3.json} 内部 {@code FileReferences} 的<b>相对路径改写为带签名的完整下载 URL</b>，
 * 并返回模型各文件字节流。模型资源整体放置于独立目录（{@link Live2dProperties#getModelRootPath()}，
 * <b>不入库 static-resources 静态映射</b>），因此裸链接 404 也无法访问，下载流只经此签名校验放行。</p>
 *
 * <p><b>为什么必须改写（前端零成本的来源）</b>：Live2D 运行时加载模型时基于 {@code model3.json}
 * 的当前 URL 去解析其内部声明的相对路径（如 {@code Textures[]} / {@code Motions[].File} /
 * {@code Expressions[].File} / {@code Physics} 等）。若这些子路径不带签名，运行时发出的子请求
 * 就是裸路径 → 无法认证。因此本类在返回 model3.json 时实时解析其结构，逐条子资源签发带有效期的
 * 签名 URL，前端拿到即直接用。</p>
 *
 * <p><b>签名有效期</b>：默认 5 分钟（够一次模型加载），会话中动态加载 motion/expression 若发现
 * 签名过期，可重新调用用户端 config 重新签发入口 model3.json。签名校验绑定模型根目录（path 前缀
 * 校验 + normalize 防穿越 + HMAC 比对），同一模型内子资源均合法。</p>
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelFileSigner {

    /**
     * 防目录穿越的兜底密钥（{@code live2d.file-sign.secret} 未配置时使用）。
     * <p>此值属公开约定，仅保证功能演示；生产环境必须在 application.yaml/配置中心覆盖
     * {@code live2d.file-sign.secret}，否则签名可被反向推测、模型文件可被任意下载。</p>
     */
    private static final String FALLBACK_SECRET = "ai-mental-care-live2d-file-sign";

    /** 种子签名指向的下载接口相对路径（Controller 路由拼接；需与 Live2dConfigController 路由一致） */
    public static final String DOWNLOAD_PATH = "/user/live2d-model/file";

    private final Live2dProperties live2dProperties;

    /** 签名密钥：优先配置值，为空时兜底默认值并告警 */
    private String activeSecret() {
        String configured = live2dProperties.getFileSign().getSecret();
        if (configured == null || configured.isBlank()) {
            log.warn("[模型文件签名器] 未配置 live2d.file-sign.secret，使用内置默认密钥，生产环境务必覆盖！");
            return FALLBACK_SECRET;
        }
        return configured;
    }

    /** 签名有效期（秒）：优先配置值，非法时兜底默认 300 秒 */
    private long expireSeconds() {
        Long configured = live2dProperties.getFileSign().getExpireSeconds();
        return configured != null && configured > 0 ? configured : 300L;
    }

    /** 模型根目录（含尾斜杠），相对运行目录；与 {@link Live2dProperties#getModelRootPath()} 一致 */
    private String modelRoot() {
        String root = live2dProperties.getModelRootPath();
        if (root == null || root.isBlank()) {
            log.warn("[模型文件签名器] 未配置 live2d.model-root-path，使用默认值 live2d-models/");
            root = "live2d-models/";
        }
        return root.endsWith("/") ? root : root + "/";
    }

    /**
     * 校验并归一化模型文件相对路径。
     *
     * @param modelName 模型名称
     * @param subPath   包内相对路径（相对模型根目录）
     * @return 服务端完整相对路径（相对运行目录）
     */
    public String resolvePath(String modelName, String subPath) {
        String modelDir = modelRoot() + modelName;
        Path root = Paths.get(modelRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(modelName).resolve(subPath).normalize();
        if (!target.startsWith(root.resolve(modelName))) {
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_PATH_INVALID);
        }
        return modelDir + "/" + subPath.replace("\\", "/");
    }

    /**
     * 校验下载参数并返回文件字节。
     *
     * @param path    请求携带的完整相对路径（servlet 相对运行目录，如 live2d-models/hiyori/hiyori.model3.json）
     * @param expires 签名过期时间戳（Unix 秒）
     * @param sign    签名
     * @return 文件字节与是否需要 model3 改写（改写后返回给前端）
     */
    public ModelFileBytes load(String path, long expires, String sign) {
        // 1. 路径前缀校验：必须位于模型根目录下（归一化后），防目录穿越
        String normalized = normalizeAndCheckPath(path);
        if (normalized == null) {
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_PATH_INVALID);
        }

        // 2. 签名校验：HMAC(path + expires) 比对 + 过期校验
        if (!SignatureUtil.verify(normalized, expires, sign, activeSecret())) {
            log.warn("[模型文件签名器] 签名校验失败，path：{}，expires：{}", normalized, expires);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_SIGN_INVALID);
        }

        // 3. 读取文件
        Path file = Paths.get(normalized).toAbsolutePath().normalize();
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            log.warn("[模型文件签名器] 文件不存在，path：{}", normalized);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_FILE_NOT_FOUND);
        }

        try {
            byte[] bytes = Files.readAllBytes(file);
            boolean isModel3 = normalized.toLowerCase().endsWith(".model3.json");
            if (isModel3) {
                // 对 model3.json 实时改写 FileReferences 相对路径 → 签名 URL
                bytes = rewriteModel3(bytes, normalized);
            }
            return new ModelFileBytes(bytes, isModel3);
        } catch (IOException e) {
            log.error("[模型文件签名器] 读取文件失败，path：{}", normalized, e);
            throw new BusinessException(Live2dConfigExceptionEnum.LIVE2D_MODEL_FILE_NOT_FOUND);
        }
    }

    /**
     * 归一化并校验路径必须在模型根目录下
     */
    private String normalizeAndCheckPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace("\\", "/");
        // 移除前导 / 与 ./ 片段
        while (normalized.startsWith("/") || normalized.startsWith("./")) {
            normalized = normalized.startsWith("/") ? normalized.substring(1) : normalized.substring(2);
        }
        Path target = Paths.get(normalized).normalize();
        Path root = Paths.get(modelRoot()).normalize();
        if (!target.startsWith(root)) {
            log.warn("[模型文件签名器] 路径越权访问被拒绝，path：{}", path);
            return null;
        }
        return target.toString().replace("\\", "/");
    }

    /**
     * 将 model3.json 中 FileReferences 下所有相对路径改写为带签名的下载 URL
     */
    private byte[] rewriteModel3(byte[] bytes, String normalizedPath) throws IOException {
        JsonNode root = JsonUtils.readTree(bytes);
        if (!(root instanceof ObjectNode objectNode)) {
            return bytes;
        }

        // 模型目录前缀（{modelRoot}/{modelName}/）
        String modelPrefix = normalizedPath.substring(0, normalizedPath.lastIndexOf('/') + 1);
        long expires = System.currentTimeMillis() / 1000 + expireSeconds();

        // 定位 FileReferences
        JsonNode fileRefs = objectNode.get("FileReferences");
        if (fileRefs instanceof ObjectNode fileRefsNode) {
            // 直接命名的相对路径字段（Cubism 规范固定键名）
            List<String> singlePathFields = List.of("Moc", "Physics", "Pose", "DisplayInfo", "UserData");
            for (String field : singlePathFields) {
                JsonNode value = fileRefsNode.get(field);
                if (value != null && value.isTextual()) {
                    fileRefsNode.put(field, signUrl(modelPrefix, value.asText(), expires));
                }
            }
            // Textures：字符串数组
            JsonNode textures = fileRefsNode.get("Textures");
            if (textures != null && textures.isArray()) {
                ArrayNode newTextures = JsonUtils.createArrayNode();
                for (JsonNode tex : textures) {
                    if (tex.isTextual()) {
                        newTextures.add(signUrl(modelPrefix, tex.asText(), expires));
                    } else {
                        newTextures.add(tex);
                    }
                }
                fileRefsNode.set("Textures", newTextures);
            }

            // Motions：{ "Idle": [ { "File": "..." }, ... ] }
            JsonNode motions = fileRefsNode.get("Motions");
            if (motions instanceof ObjectNode motionsNode) {
                rewriteMotionMap(motionsNode, modelPrefix, expires);
            }
            // Expressions：[ { "Name": xxx, "File": "..." }, ... ]
            JsonNode expressions = fileRefsNode.get("Expressions");
            if (expressions != null && expressions.isArray()) {
                rewriteExpressions((ArrayNode) expressions, modelPrefix, expires);
            }
        }

        return JsonUtils.writeValueAsBytes(objectNode);
    }

    /**
     * Motions 组：每次动作组内每条的 File 与 Duration 等字段
     */
    private void rewriteMotionMap(ObjectNode motionsNode, String modelPrefix, long expires) {
        Iterator<Map.Entry<String, JsonNode>> groups = motionsNode.fields();
        while (groups.hasNext()) {
            Map.Entry<String, JsonNode> group = groups.next();
            JsonNode motions = group.getValue();
            if (motions.isArray()) {
                for (JsonNode motion : motions) {
                    if (motion instanceof ObjectNode motionNode && motionNode.has("File")) {
                        String file = motionNode.get("File").asText();
                        if (!file.isBlank()) {
                            motionNode.put("File", signUrl(modelPrefix, file, expires));
                        }
                    }
                }
            }
        }
    }

    /**
     * Expressions 数组：每条表达式有 Name + File
     */
    private void rewriteExpressions(ArrayNode expressions, String modelPrefix, long expires) {
        for (JsonNode expression : expressions) {
            if (expression instanceof ObjectNode expressionNode && expressionNode.has("File")) {
                String file = expressionNode.get("File").asText();
                if (!file.isBlank()) {
                    expressionNode.put("File", signUrl(modelPrefix, file, expires));
                }
            }
        }
    }

    /**
     * 为单个子资源签发带签名的完整下载 URL
     */
    private String signUrl(String modelPrefix, String subPath, long expires) {
        String fullPath = modelPrefix + subPath.replace("\\", "/");
        String sign = SignatureUtil.sign(fullPath, expires, activeSecret());
        return DOWNLOAD_PATH + "?path=" + fullPath + "&expires=" + expires + "&sign=" + sign;
    }

    /**
     * 根据文件扩展名推断 HTTP Content-Type（Live2D 文件无标准 MIME，按常见约定）
     */
    public static MediaType resolveContentTypeStatic(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM; // .moc3/.motion3/.exp3/纹理等默认二进制
    }

    /**
     * 加载的模型文件字节与元信息封装
     *
     * @param bytes     文件字节（model3 为改写后）
     * @param isModel3  是否为 model3.json（供 Controller 决定 Content-Type）
     */
    public record ModelFileBytes(byte[] bytes, boolean isModel3) {
    }
}