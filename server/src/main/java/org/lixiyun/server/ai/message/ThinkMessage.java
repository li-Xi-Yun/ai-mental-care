package org.lixiyun.server.ai.message;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;

import java.util.Map;
import java.util.Objects;

/**
 * @author lixiyun
 * @since 2026-03-22 13:46
 */
public class ThinkMessage extends AbstractMessage {

    public ThinkMessage(String content) {
        super(MessageType.ASSISTANT, content, Map.of());
    }

    protected ThinkMessage(MessageType messageType, @Nullable String textContent, Map<String, Object> metadata) {
        super(messageType, textContent, metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThinkMessage that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(this.textContent, that.textContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.textContent);
    }

    @Override
    public String toString() {
        return "ThinkMessage [messageType=" + this.messageType + ", textContent="
                + this.textContent + ", metadata=" + this.metadata + "]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String content;

        private Builder() {
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public ThinkMessage build() {
            return new ThinkMessage(this.content);
        }

    }
}
