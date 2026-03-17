package org.lixiyun.server.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.properties.FileUrlProperties;
import org.lixiyun.server.service.UploadService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author lixiyun
 * @since 2025-12-28 20:54
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final FileUrlProperties fileUrlProperties;

    @Override
    public String imageUpload(MultipartFile file) {
        LocalDateTime currentDate  = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String format = currentDate.format(formatter);
        String newFileName  = format + StrUtil.uuid() + file.getOriginalFilename();


        Path targetDir = Paths.get(fileUrlProperties.getUploadImages());
        try {
            // 确保目录存在
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            Path targetPath = Paths.get(fileUrlProperties.getUploadImages()).resolve(newFileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return fileUrlProperties.getUploadImages() + newFileName;
    }

}
