package com.bugsight.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bugsight.common.exception.BusinessException;
import com.bugsight.common.result.ResultCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class InferenceService {

    @Value("${inference.url}")
    private String inferenceUrl;

    @Data
    public static class InferenceItem {
        private Integer classIndex;
        private Double confidence;
    }

    @Data
    public static class InferenceResult {
        private Boolean isUnknown;
        private InferenceItem top1;
        private List<InferenceItem> top3;
    }

    /**
     * 调用 FastAPI /predict 接口完成图片推理
     */
    public InferenceResult predict(MultipartFile file) {
        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
            String response = HttpRequest.post(inferenceUrl + "/predict")
                    .form("file", file.getBytes(), filename)
                    .execute()
                    .body();
            JSONObject json = JSONUtil.parseObj(response);

            InferenceResult result = new InferenceResult();
            result.setIsUnknown(json.getBool("isUnknown", false));

            JSONObject top1Json = json.getJSONObject("top1");
            if (top1Json != null && !top1Json.isEmpty()) {
                InferenceItem top1 = new InferenceItem();
                top1.setClassIndex(top1Json.getInt("classIndex"));
                top1.setConfidence(top1Json.getDouble("confidence"));
                result.setTop1(top1);
            }

            List<InferenceItem> top3 = json.getJSONArray("top3") != null
                    ? json.getJSONArray("top3").toList(InferenceItem.class)
                    : Collections.emptyList();
            result.setTop3(top3);

            return result;
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BusinessException(ResultCode.INFERENCE_FAILED);
        } catch (Exception e) {
            log.error("推理服务调用失败", e);
            throw new BusinessException(ResultCode.INFERENCE_FAILED);
        }
    }
}
