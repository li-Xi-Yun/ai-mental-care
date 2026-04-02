package org.lixiyun.server.service;

import org.lixiyun.pojo.dto.conversation.AudioModelDTO;

import java.io.OutputStream;

/**
 * @author lixiyun
 * @since 2026-03-29 18:17
 */
public interface AudioService {

    void audioModel(AudioModelDTO audioModelDTO, OutputStream outputStream);

}
