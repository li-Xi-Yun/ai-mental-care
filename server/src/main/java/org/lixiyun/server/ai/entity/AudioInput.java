package org.lixiyun.server.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author lixiyun
 * @since 2026-03-31 09:42
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioInput implements Serializable {

    private static final long serialVersionUID = 1L;

    private byte[] audioData;
}
