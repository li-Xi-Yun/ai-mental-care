package org.lixiyun.server.enums;

public enum MessageType {
        USER("user"),
        ASSISTANT("assistant"),
        SYSTEM("system"),
        TOOL("tool"),
        ASSISTANT_TOOL("assistant_tool"),
        THINKING("thinking");

        String name;

        MessageType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
