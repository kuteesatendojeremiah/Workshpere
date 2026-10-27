package com.worksphere.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.storage.*}: the S3-compatible object store (MinIO in development). */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String endpoint, String accessKey, String secretKey, String bucket) {
}
