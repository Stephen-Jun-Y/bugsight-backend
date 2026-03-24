package com.bugsight.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String provider = "local";
    private Minio minio = new Minio();

    @Data
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey;
        private String secretKey;
        private String bucket = "bugsight-images";
    }
}
