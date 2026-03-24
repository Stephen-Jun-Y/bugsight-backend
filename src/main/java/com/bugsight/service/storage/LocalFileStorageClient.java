package com.bugsight.service.storage;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Slf4j
@Service("localStorageClient")
public class LocalFileStorageClient implements FileStorageClient {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Override
    public void save(String objectKey, MultipartFile file, String contentType) {
        String dest = uploadPath + File.separator + objectKey;
        try {
            FileUtil.mkParentDirs(dest);
            file.transferTo(new File(dest));
        } catch (IOException e) {
            log.error("本地文件保存失败", e);
            throw new IllegalStateException("Failed to save file locally", e);
        }
    }

    @Override
    public Optional<StoredFileObject> load(String objectKey) {
        File file = new File(uploadPath + File.separator + objectKey);
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            String contentType = FileUtil.getMimeType(file.getName());
            if (contentType == null || contentType.isBlank()) {
                contentType = "image/jpeg";
            }
            return Optional.of(new StoredFileObject(
                    contentType,
                    FileUtil.readBytes(file)
            ));
        } catch (Exception e) {
            log.error("本地文件读取失败", e);
            throw new IllegalStateException("Failed to load local file", e);
        }
    }
}
