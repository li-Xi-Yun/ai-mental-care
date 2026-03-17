package org.lixiyun.common.vector.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FileTypeDetectorUtil {

    private static final Map<String, String> FILE_SIGNATURES = new HashMap<>();
    
    static {
        // 常见文件类型的魔数签名
        // 常见文件类型的魔数签名

        FILE_SIGNATURES.put("504B0304", "docx"); // DOCX (也是ZIP格式)
//        FILE_SIGNATURES.put("504B0304", "pptx"); // PPTX (ZIP格式)
//        FILE_SIGNATURES.put("504B0304", "XLSX"); // XLSX (ZIP格式)

        FILE_SIGNATURES.put("504B0708", "docx"); // DOCX (分卷文档)
//        FILE_SIGNATURES.put("504B0708", "pptx"); // PPTX (分卷文档)

        FILE_SIGNATURES.put("504B0506", "pptx"); // PPTX (空文档)
//        FILE_SIGNATURES.put("504B0506", "docx"); // DOCX (空文档)

        FILE_SIGNATURES.put("D0CF11E0", "doc"); // 旧版Word DOC
//        FILE_SIGNATURES.put("D0CF11E0", "xls"); // 旧版Excel XLS
//        FILE_SIGNATURES.put("D0CF11E0", "ppt"); // 旧版PowerPoint PPT

        FILE_SIGNATURES.put("FFD8FFE0", "jpeg"); // JPEG: FF D8 FF E0
        FILE_SIGNATURES.put("FFD8FFE1", "jpeg"); // JPEG: FF D8 FF E1

        FILE_SIGNATURES.put("25504446", "pdf"); // PDF
        FILE_SIGNATURES.put("47494638", "gif"); // GIF: GIF8
        FILE_SIGNATURES.put("424D", "bmp"); // BMP: BM
        FILE_SIGNATURES.put("89504E47", "png"); // PNG: \x89PNG
        // Markdown是文本文件，没有特定魔数，通常通过扩展名和内容判断
    }

    public static String detectFileType(InputStream file, String fileName) throws IOException {
            byte[] header = new byte[4]; // 读取前4个字节通常足够
            int bytesRead = file.read(header);
            if (bytesRead < 4) {
                return "unknown"; // 文件太短
            }

            // 将字节转换为十六进制字符串
            StringBuilder hexBuilder = new StringBuilder();
            for (int i = 0; i < bytesRead; i++) {
                hexBuilder.append(String.format("%02X", header[i] & 0xFF));
            }
            String hexHeader = hexBuilder.toString();

            String s = fileName.toLowerCase();
            if (s.endsWith(".docx")) {
                return "docx";
            } else if (s.endsWith(".pptx")) {
                return "pptx";
            } else if (s.endsWith(".xlsx")) {
                return "xlsx";
            } else if (s.endsWith(".doc")) {
                return "doc";
            } else if (s.endsWith(".ppt")) {
                return "ppt";
            } else if (s.endsWith(".xls")) {
                return "xls";
            }

            // 匹配已知签名
            for (Map.Entry<String, String> entry : FILE_SIGNATURES.entrySet()) {
                if (hexHeader.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }


            String lowerFileName = fileName.toLowerCase();
            if (lowerFileName.endsWith(".html") || lowerFileName.endsWith(".htm")) {
                return "html";
            } else if (lowerFileName.endsWith(".txt")) {
                return "txt";
            } else if (lowerFileName.endsWith(".md") || lowerFileName.endsWith(".markdown")) {
                return "markdown";
            }
            return "unknown";
    }

    // 简单判断是否为文本文件
    private static boolean isTextFile(byte[] data, int length) {
        for (int i = 0; i < length; i++) {
            byte b = data[i];
            // 检查是否为非文本控制字符（排除换行符、回车符、制表符）
            if ((b < 0x20 || b > 0x7E) && 
                 b != 0x0A && b != 0x0D && b != 0x09) {
                return false;
            }
        }
        return true;
    }
}