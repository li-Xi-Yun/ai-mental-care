package org.lixiyun.server.ai.model.factory;

/**
 * @author lixiyun
 * @since 2026-08-15 17:42
 */
public enum ChatModelType {

    OLLAMA("ollama", "getOllamaChatModel", 0),
    DEEP_SEEK("deepSeek", "getDeepSeekChatModel", 1),
    DASH_SCOPE("dashScope", "getDashScopeChatModel", 2);

    /** 工厂标识key */
    public final String factoryKey;
    /** ChatModelFactory 对应的getter方法名 {@link ChatModelFactory} */
    public final String getterMethod;
    /** 模型类型 */
    public final int type;

    ChatModelType(String factoryKey, String getterMethod, int type) {
        this.factoryKey = factoryKey;
        this.getterMethod = getterMethod;
        this.type = type;
    }

    public static ChatModelType fromType(Integer type) {
        if (type == null) {
            return DEEP_SEEK;
        }
        for (ChatModelType value : values()) {
            if (value.type == type) {
                return value;
            }
        }
        return DEEP_SEEK;
    }
}