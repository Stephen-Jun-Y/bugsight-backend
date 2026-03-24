package com.bugsight.service;

import com.bugsight.service.storage.FileStorageClient;
import com.bugsight.service.storage.StoredFileObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceStorageTest {

    @Mock
    private FileStorageClient localStorageClient;
    @Mock
    private FileStorageClient minioStorageClient;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(localStorageClient, minioStorageClient);
        ReflectionTestUtils.setField(fileService, "accessUrl", "http://example.com/api/v1/files");
        ReflectionTestUtils.setField(fileService, "storageProvider", "local");
    }

    @Test
    void savesImagesToMinioWhenConfigured() {
        ReflectionTestUtils.setField(fileService, "storageProvider", "minio");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.jpg",
                "image/jpeg",
                "jpeg-content".getBytes(StandardCharsets.UTF_8)
        );

        String url = fileService.saveImage(file);

        verify(minioStorageClient).save(any(), eq(file), eq("image/jpeg"));
        verify(localStorageClient, never()).save(any(), any(), any());
        assertTrue(url.startsWith("http://example.com/api/v1/files/"));
    }

    @Test
    void fallsBackToLocalStorageWhenMinioMissesObject() {
        ReflectionTestUtils.setField(fileService, "storageProvider", "minio");
        StoredFileObject localObject = new StoredFileObject("image/png", "fallback".getBytes(StandardCharsets.UTF_8));
        when(minioStorageClient.load("legacy.png")).thenReturn(Optional.empty());
        when(localStorageClient.load("legacy.png")).thenReturn(Optional.of(localObject));

        Optional<StoredFileObject> loaded = fileService.loadImage("legacy.png");

        assertTrue(loaded.isPresent());
        assertSame(localObject, loaded.get());
        verify(minioStorageClient).load("legacy.png");
        verify(localStorageClient).load("legacy.png");
    }

    @Test
    void keepsUsingLocalStorageInDevelopmentMode() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "png-content".getBytes(StandardCharsets.UTF_8)
        );

        String url = fileService.saveImage(file);

        verify(localStorageClient).save(any(), eq(file), eq("image/png"));
        verify(minioStorageClient, never()).save(any(), any(), any());
        assertEquals("http://example.com/api/v1/files/" + url.substring("http://example.com/api/v1/files/".length()), url);
    }
}
