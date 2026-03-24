package com.bugsight.service.storage;

import com.bugsight.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Service("minioStorageClient")
@RequiredArgsConstructor
public class MinioFileStorageClient implements FileStorageClient {

    private final ObjectProvider<MinioClient> minioClientProvider;
    private final StorageProperties storageProperties;

    private volatile boolean bucketReady = false;

    @Override
    public void save(String objectKey, MultipartFile file, String contentType) {
        MinioClient client = requireClient();
        ensureBucket(client);
        try (InputStream inputStream = file.getInputStream()) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(storageProperties.getMinio().getBucket())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO 文件保存失败", e);
            throw new IllegalStateException("Failed to save file to MinIO", e);
        }
    }

    @Override
    public Optional<StoredFileObject> load(String objectKey) {
        MinioClient client = minioClientProvider.getIfAvailable();
        if (client == null) {
            return Optional.empty();
        }

        try {
            var stat = client.statObject(
                    StatObjectArgs.builder()
                            .bucket(storageProperties.getMinio().getBucket())
                            .object(objectKey)
                            .build()
            );
            try (GetObjectResponse response = client.getObject(
                    GetObjectArgs.builder()
                            .bucket(storageProperties.getMinio().getBucket())
                            .object(objectKey)
                            .build()
            )) {
                return Optional.of(new StoredFileObject(
                        stat.contentType(),
                        toBytes(response)
                ));
            }
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() != null ? e.errorResponse().code() : "";
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code)) {
                return Optional.empty();
            }
            log.error("MinIO 文件读取失败", e);
            throw new IllegalStateException("Failed to load file from MinIO", e);
        } catch (Exception e) {
            log.error("MinIO 文件读取失败", e);
            throw new IllegalStateException("Failed to load file from MinIO", e);
        }
    }

    private MinioClient requireClient() {
        MinioClient client = minioClientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("MinIO client is not configured");
        }
        return client;
    }

    private void ensureBucket(MinioClient client) {
        if (bucketReady) {
            return;
        }

        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists = client.bucketExists(
                        BucketExistsArgs.builder()
                                .bucket(storageProperties.getMinio().getBucket())
                                .build()
                );
                if (!exists) {
                    client.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(storageProperties.getMinio().getBucket())
                                    .build()
                    );
                }
                bucketReady = true;
            } catch (Exception e) {
                log.error("MinIO bucket 初始化失败", e);
                throw new IllegalStateException("Failed to initialize MinIO bucket", e);
            }
        }
    }

    private byte[] toBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        return outputStream.toByteArray();
    }
}
