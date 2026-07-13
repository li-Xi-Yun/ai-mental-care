package org.lixiyun.common.sms.core.template;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.utils.OkHttpUtil;
import org.lixiyun.common.sms.core.SmsTemplate;
import org.lixiyun.common.sms.entity.SmsResult;
import org.lixiyun.common.sms.exception.SmsException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于spug的短信服务
 * @author lixiyun
 * @since 2026-05-07 19:25
 */
@Slf4j
@Component
public class SpugSmsTemplate implements SmsTemplate {

    // Spug短信服务的API地址
    private static final String SPUG_SMS_API = "https://push.spug.cc/sms/";

    @Override
    public SmsResult send(String phones, String templateId, Map<String, String> param) {
        try {
            log.info("发送短信，手机号：{}，模板ID：{}，参数：{}", phones, templateId, param);
            if (phones.isBlank()) {
                throw new SmsException("手机号不能为空");
            }
            // 构建API URL（模板ID在URL路径中）
            String url = SPUG_SMS_API + templateId;

            // 添加手机号参数
            Map<String, String> queryParams = new HashMap<>();
            if(param != null){
                queryParams.putAll(param);
            }
            queryParams.put("to", phones);

            // 创建OkHttpUtil实例
            OkHttpUtil okHttpUtil = new OkHttpUtil();
            
            // 发送POST请求
            JSONObject result = okHttpUtil.post(url, null, queryParams, null);

            log.info("Spug短信发送结果：{}", result);
            // 解析响应结果
            if (result != null) {
                // 根据Spug API响应格式解析结果
                // code=200表示接口请求成功，msg为返回信息
                boolean success = result.containsKey("code") && result.getInteger("code") == 200;
                String message = result.getString("msg");
                
                return SmsResult.builder()
                        .isSuccess(success)
                        .message(message)
                        .response(result.toJSONString())
                        .build();
            } else {
                return SmsResult.builder()
                        .isSuccess(false)
                        .message("请求失败，无响应")
                        .build();
            }
        } catch (Exception e) {
            log.error("Spug短信发送失败: {}", e.getMessage(), e);
            return SmsResult.builder()
                    .isSuccess(false)
                    .message("短信发送异常: " + e.getMessage())
                    .build();
        }
    }
}
