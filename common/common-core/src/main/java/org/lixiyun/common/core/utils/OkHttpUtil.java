package org.lixiyun.common.core.utils;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Data
@Slf4j
@NoArgsConstructor
public class OkHttpUtil {

    private String apiHost;
    private Map<String, String> apiHeaders;
    private Function<Request, Request> frontProcessor;
    private Function<JSONObject, JSONObject> postProcessor;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.SECONDS)
            .writeTimeout(3000, TimeUnit.SECONDS)
            .readTimeout(3000, TimeUnit.SECONDS)
            .build();

    public OkHttpUtil(String apiHost, Map<String, String> apiHeaders, Function<Request, Request> frontProcessor, Function<JSONObject, JSONObject> postProcessor) {
        this.apiHost = apiHost;
        this.apiHeaders = apiHeaders;
        this.frontProcessor = frontProcessor;
        this.postProcessor = postProcessor;
    }

    public JSONObject post(String url, String json, Map<String, String> queryPrams, Map<String, String> headers) {
        Request request = createPostRequest(url, json, queryPrams, headers);
        return executeRequest(request);
    }

    public JSONObject post(String url, Object entity, Map<String, String> queryPrams, Map<String, String> headers) {
        try {
            String jsonString = JsonUtils.toJsonString(entity);
            return post(url, jsonString, queryPrams, headers);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONObject post(String url, Object entity, Map<String, String> queryPrams) {
        return post(url, entity, queryPrams, null);
    }

    public JSONObject post(String url, Object entity) {
        return post(url, entity, null);
    }

    public JSONObject get(String url, Map<String, String> queryPrams, Map<String, String> headers) {
        Request request = createGetRequest(url, queryPrams, headers);
        return executeRequest(request);
    }

    public JSONObject get(String url, Map<String, String> queryPrams) {
        return get(url, queryPrams, null);
    }

    public JSONObject get(String url) {
        return get(url, null);
    }

    public JSONObject executeRequest(Request request) {
        if(frontProcessor != null){
            request = frontProcessor.apply(request);
            if(request == null){
                return null;
            }
        }
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Request failed: {}", response);
                throw new IOException("Unexpected code " + response);
            }
            JSONObject result = response.body() != null ? JSONObject.parseObject(response.body().string()) : null;
            log.debug("Request successful: {}", result);
            if(postProcessor != null){
                result = postProcessor.apply(result);
            }
            return result;
        } catch (IOException e) {
            log.error("Request execution failed: {}", e.getMessage(), e);
            return null;
        }
    }

    public Request createPostRequest(String url, String json, Map<String, String> queryPrams, Map<String, String> headers) {
        if(queryPrams != null && !queryPrams.isEmpty()){
            String s = queryPrams.entrySet().stream().map(item -> item.getKey() + "=" + item.getValue())
                    .reduce((a, b) -> a + "&" + b).orElse("");
            if(StrUtil.isNotBlank(s)){
                url += "?" + s;
            }
        }

        Request.Builder builder = new Request.Builder()
                .url(apiHost + url);
        if(json != null){
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(json, JSON);
            builder.post(body);
        }
        if(apiHeaders != null){
            builder.headers(Headers.of(apiHeaders));
        }
        if(headers != null){
            builder.headers(Headers.of(headers));
        }
        return builder.build();
    }

    public Request createGetRequest(String url, Map<String, String> queryPrams, Map<String, String> headers) {
        if(queryPrams != null && !queryPrams.isEmpty()){
            String s = queryPrams.entrySet().stream().map(item -> item.getKey() + "=" + item.getValue())
                    .reduce((a, b) -> a + "&" + b).orElse("");
            if(StrUtil.isNotBlank(s)){
                url += "?" + s;
            }
        }

        Request.Builder builder = new Request.Builder()
                .url(apiHost + url);
        if(apiHeaders != null){
            builder.headers(Headers.of(apiHeaders));
        }
        if(headers != null){
            builder.headers(Headers.of(headers));
        }
        return builder.build();
    }

}
