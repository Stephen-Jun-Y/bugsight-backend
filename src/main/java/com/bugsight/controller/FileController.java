package com.bugsight.controller;

import com.bugsight.common.result.Result;
import com.bugsight.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "文件模块")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传图片（头像等）")
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileService.saveImage(file);
        return Result.ok(Map.of("url", url));
    }

    @Operation(summary = "访问静态文件")
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> get(@PathVariable String filename) {
        var stored = fileService.loadImage(filename);
        if (stored.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaType.IMAGE_JPEG;
        String contentType = stored.get().contentType();
        if (contentType != null && !contentType.isBlank()) {
            mediaType = MediaType.parseMediaType(contentType);
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new ByteArrayResource(stored.get().bytes()));
    }
}
