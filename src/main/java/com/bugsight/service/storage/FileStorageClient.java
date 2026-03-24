package com.bugsight.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface FileStorageClient {

    void save(String objectKey, MultipartFile file, String contentType);

    Optional<StoredFileObject> load(String objectKey);
}
