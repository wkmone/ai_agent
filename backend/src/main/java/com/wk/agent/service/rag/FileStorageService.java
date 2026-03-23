package com.wk.agent.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadDir;

    public String storeFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String newFilename = datePrefix + "_" + uniqueId + extension;

            Path targetLocation = uploadPath.resolve(newFilename);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("文件存储成功: {} -> {}", originalFilename, targetLocation);
            return targetLocation.toString();

        } catch (IOException e) {
            log.error("文件存储失败: {}", e.getMessage());
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("文件删除成功: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("文件删除失败: {}", e.getMessage());
        }
    }

    public boolean isValidFileType(String filename) {
        if (filename == null) {
            return false;
        }
        String lowerName = filename.toLowerCase();
        return lowerName.endsWith(".txt") ||
               lowerName.endsWith(".md") ||
               lowerName.endsWith(".markdown") ||
               lowerName.endsWith(".html") ||
               lowerName.endsWith(".htm") ||
               lowerName.endsWith(".pdf") ||
               lowerName.endsWith(".doc") ||
               lowerName.endsWith(".docx") ||
               lowerName.endsWith(".ppt") ||
               lowerName.endsWith(".pptx") ||
               lowerName.endsWith(".json") ||
               lowerName.endsWith(".csv");
    }

    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
