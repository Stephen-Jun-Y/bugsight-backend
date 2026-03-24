package com.bugsight.service;

import com.bugsight.common.exception.BusinessException;
import com.bugsight.common.result.ResultCode;
import com.bugsight.service.storage.FileStorageClient;
import com.bugsight.service.storage.StoredFileObject;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    @Qualifier("localStorageClient")
    private final FileStorageClient localStorageClient;

    @Qualifier("minioStorageClient")
    private final FileStorageClient minioStorageClient;

    @Value("${storage.provider:local}")
    private String storageProvider;

    @Value("${file.access-url}")
    private String accessUrl;

    /**
     * 保存上传图片，返回可访问 URL
     */
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ResultCode.IMAGE_FORMAT_INVALID);
        }

        String ext = FileUtil.extName(file.getOriginalFilename());
        String filename = IdUtil.fastSimpleUUID() + "." + ext;
        try {
            primaryStorage().save(filename, file, contentType);
        } catch (IllegalStateException e) {
            log.error("文件保存失败", e);
            throw new BusinessException(ResultCode.SERVER_ERROR);
        }
        return accessUrl + "/" + filename;
    }

    public Optional<StoredFileObject> loadImage(String objectKey) {
        try {
            Optional<StoredFileObject> primary = primaryStorage().load(objectKey);
            if (primary.isPresent()) {
                return primary;
            }
            if (useMinio()) {
                return localStorageClient.load(objectKey);
            }
            return Optional.empty();
        } catch (IllegalStateException e) {
            log.error("文件读取失败", e);
            throw new BusinessException(ResultCode.SERVER_ERROR);
        }
    }

    private FileStorageClient primaryStorage() {
        return useMinio() ? minioStorageClient : localStorageClient;
    }

    private boolean useMinio() {
        return "minio".equalsIgnoreCase(storageProvider);
    }
}
