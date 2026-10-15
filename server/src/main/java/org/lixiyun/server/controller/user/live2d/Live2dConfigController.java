package org.lixiyun.server.controller.user.live2d;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.pojo.vo.user.live2d.Live2dConfigVO;
import org.lixiyun.server.infrastructure.live2d.ModelFileSigner;
import org.lixiyun.server.service.user.Live2dConfigService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端 Live2D 模型配置相关接口
 * <p>会话初始化时前端调用 config 获取当前启用模型，按 modelUrl 动态加载，按白名单校验收到的资产 id；
 * 模型文件通过 <code>file</code> 接口<b>签名认证下载</b>（不入库静态资源，避免裸链可下载）。</p>
 *
 * @author lixiyun
 * @since 2026-10-14
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/live2d-model")
@Tag(name = "用户端Live2D模型配置接口", description = "用户端Live2D模型配置接口")
public class Live2dConfigController {

    private final Live2dConfigService live2dConfigService;

    @GetMapping("/config")
    @Operation(summary = "获取当前启用的Live2D模型配置", description = "返回启用模型的 modelUrl、封面图与表情/动作白名单；无启用模型时 data 为 null（前端判空不渲染 Live2D）")
    public Result<Live2dConfigVO> getEnabledConfig() {
        log.info("获取当前启用的Live2D模型配置");
        Live2dConfigVO vo = live2dConfigService.getEnabledConfig();
        return Result.success(vo);
    }

    @GetMapping("/list")
    @Operation(summary = "获取全部可用Live2D模型列表", description = "返回所有未删除模型的配置，用于模型选择与预览")
    public Result<List<Live2dConfigVO>> listAvailable() {
        log.info("获取全部可用Live2D模型列表");
        List<Live2dConfigVO> list = live2dConfigService.listAvailable();
        return Result.success(list);
    }


    @Operation(summary = "认证下载Live2D模型文件", description = "模型资源的唯一访问入口：校验签名（HMAC+有效期）与路径（必须在 live2d-models/ 下）；model3.json 实时改写内部子资源路径为带签名的下载 URL")
    public ResponseEntity<byte[]> downloadModelFile(
            @RequestParam @NotBlank(message = "模型文件路径不能为空")
            @Parameter(description = "服务端相对路径，如 live2d-models/hiyori/hiyori.model3.json", required = true) String path,
            @RequestParam @Parameter(description = "签名过期时间戳（Unix 秒）", required = true) long expires,
            @RequestParam @NotBlank(message = "签名不能为空")
            @Parameter(description = "HMAC 签名", required = true) String sign) {
        log.debug("认证下载Live2D模型文件，path：{}", path);
        ModelFileSigner.ModelFileBytes fileBytes = live2dConfigService.downloadModelFile(path, expires, sign);
        MediaType contentType = fileBytes.isModel3()
                ? MediaType.APPLICATION_JSON
                : ModelFileSigner.resolveContentTypeStatic(path);
        return ResponseEntity.ok()
                .contentType(contentType)
                .body(fileBytes.bytes());
    }
}