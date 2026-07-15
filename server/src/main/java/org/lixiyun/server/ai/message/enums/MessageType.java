package org.lixiyun.server.ai.message.enums;

public enum MessageType {
        USER("user", "用户"),
        ASSISTANT("assistant", "模型"),
        SYSTEM("system", "系统"),
        TOOL("tool", "请求工具调用"),
        ASSISTANT_TOOL("assistant_tool", "工具调用结果"),
        THINKING("thinking", "模型思考");

        String name;
        String description;

        MessageType(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public static String getDescription(String name){
            for (MessageType messageType : values()) {
                if (messageType.getName().equals(name)){
                    return messageType.description;
                }
            }
            return "";
        }
    }
