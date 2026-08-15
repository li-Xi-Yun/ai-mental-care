package org.lixiyun.server.ai.model.factory;

/**
 * @author lixiyun
 * @since 2026-08-15 17:42
 */
public enum ChatModelType {

    OLLAMA("ollama", "getOllamaChatModel"),
    DEEP_SEEK("deepSeek", "getDeepSeekChatModel"),
    DASH_SCOPE("dashScope", "getDashScopeChatModel");

    /** 工厂标识key */
    public final String factoryKey;
    /** ChatModelFactory 对应的getter方法名 {@link ChatModelFactory} */
    public final String getterMethod;

    ChatModelType(String factoryKey, String getterMethod) {
        this.factoryKey = factoryKey;
        this.getterMethod = getterMethod;
    }
}
