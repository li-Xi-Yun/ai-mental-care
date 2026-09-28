package com.alibaba.nls.token;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;

public class TokenDemo {
    // 您的地域ID
    private static final String REGIONID = "ap-southeast-1";
    // 获取Token服务域名
    private static final String DOMAIN = "nlsmeta.ap-southeast-1.aliyuncs.com";
    // API 版本
    private static final String API_VERSION = "2019-07-17";
    // API名称
    private static final String REQUEST_ACTION = "CreateToken";
    // 响应参数
    private static final String KEY_TOKEN = "Token";
    private static final String KEY_ID = "Id";
    private static final String KEY_EXPIRETIME = "ExpireTime";

    public static void main(String args[]) throws ClientException {
        if (args.length < 2) {
            System.err.println("TokenDemo need params: <AccessKey Id> <AccessKey Secret>");
            System.exit(-1);
        }
        String accessKeyId = args[0];
        String accessKeySecret = args[1];
        // 创建DefaultAcsClient实例并初始化
        DefaultProfile profile = DefaultProfile.getProfile(
            REGIONID,
            accessKeyId,
            accessKeySecret);
        IAcsClient client = new DefaultAcsClient(profile);
        CommonRequest request = new CommonRequest();
        request.setDomain(DOMAIN);
        request.setVersion(API_VERSION);
        request.setAction(REQUEST_ACTION);
        request.setMethod(MethodType.POST);
        request.setProtocol(ProtocolType.HTTPS);
        CommonResponse response = client.getCommonResponse(request);
        System.out.println(response.getData());
        if (response.getHttpStatus() == 200) {
            JSONObject result = JSON.parseObject(response.getData());
            String token = result.getJSONObject(KEY_TOKEN).getString(KEY_ID);
            long expireTime = result.getJSONObject(KEY_TOKEN).getLongValue(KEY_EXPIRETIME);
            System.out.println("获取到的Token： " + token + "，有效期时间戳(单位：秒): " + expireTime);
            // 将10位数的时间戳转换为北京时间
            String expireDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(expireTime * 1000));
            System.out.println("Token有效期的北京时间：" + expireDate);
        }
        else {
            System.out.println("获取Token失败！");
        }
    }
}
