package com.bugsight.service;

import com.bugsight.common.exception.BusinessException;
import com.bugsight.common.result.ResultCode;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;

@Slf4j
@Service
public class FileService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    @Value("${file.upload-path}")
    private String uploadPath;

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
        String dest = uploadPath + File.separator + filename;

        try {
            FileUtil.mkParentDirs(dest);
            file.transferTo(new File(dest));
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new BusinessException(ResultCode.SERVER_ERROR);
        }
        return accessUrl + "/" + filename;
    }
}
