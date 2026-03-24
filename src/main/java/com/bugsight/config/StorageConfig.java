package com.bugsight.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "storage.provider", havingValue = "minio")
    public MinioClient minioClient(StorageProperties storageProperties) {
        return MinioClient.builder()
                .endpoint(storageProperties.getMinio().getEndpoint())
                .credentials(
                        storageProperties.getMinio().getAccessKey(),
                        storageProperties.getMinio().getSecretKey()
                )
                .build();
    }
}
