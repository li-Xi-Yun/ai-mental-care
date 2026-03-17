package org.lixiyun.server.controller.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.result.Result;
import org.lixiyun.common.verification.code.utils.VerificationCodeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author lixiyun
 * @since 2025-12-21 14:26
*/
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/common/verification")
@Tag(name = "验证码相关接口", description = "验证码相关接口")
public class VerificationCodeController {

    @Value("${spring.mail.username}")
    private String from;

    @PostMapping("/code")
    @Operation(summary = "获取验证码", description = "返回值:\n" +
            "一个Map集合，包含验证码图片的Base64编码字符串和存放在Redis中验证码的答案对应的键\n" +
            "Map的key分别为\"image\"和\"key\",后一个key需要发送给前端，在校验时由前端发送，后端根据这个key进行校验")
    @ApiResponse(responseCode = "200", description = "获取成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    public Result<?> getVerificationCode(){
        log.info("获取验证码");
        Map<String, Object> map = VerificationCodeUtil.getVerificationCodePhone();
        return Result.success(map);
    }

    @PostMapping("/email")
    @Operation(summary = "发送验证码邮件", description = "向指定邮箱发送验证码")
    @ApiResponse(responseCode = "200", description = "发送成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Result<?> sendVerificationCodeEmail(@RequestParam @Email @Schema(description = "邮箱号") String email) {
        log.info("发送验证码邮件：{}", email);
        VerificationCodeUtil.sentVerificationCodeEmail(email, from);
        return Result.success();
    }
}
