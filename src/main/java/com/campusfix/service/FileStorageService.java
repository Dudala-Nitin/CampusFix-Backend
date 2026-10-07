package com.campusfix.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${campusfix.upload-dir:uploads}")
    private String uploadDir;

    /**
     * Saves an uploaded file under the upload directory with a randomized
     * name (keeping the original extension) and returns the relative path
     * to store on the entity, or null if no file was provided.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            Path root = Paths.get(uploadDir);
            Files.createDirectories(root);

            String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
            String extension = "";
            int dot = original.lastIndexOf('.');
            if (dot >= 0) {
                extension = original.substring(dot);
            }
            String filename = UUID.randomUUID() + extension;

            Path target = root.resolve(filename);
            Files.copy(file.getInputStream(), target);
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }
    }
}
