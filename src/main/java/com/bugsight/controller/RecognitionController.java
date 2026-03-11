package com.bugsight.controller;

import com.bugsight.common.result.Result;
import com.bugsight.common.utils.LoginUserUtil;
import com.bugsight.dto.request.EditHistoryRequest;
import com.bugsight.dto.response.PageResponse;
import com.bugsight.dto.response.RecognitionResponse;
import com.bugsight.entity.RecognitionHistory;
import com.bugsight.service.RecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "识别模块")
@RestController
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionService recognitionService;

    @Operation(summary = "上传图片识别")
    @PostMapping({"/recognitions", "/recognize"})
    public Result<RecognitionResponse> recognize(
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "source", defaultValue = "1") Integer source,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "locationName", required = false) String locationName,
            @RequestParam(value = "latitude", required = false) BigDecimal lat,
            @RequestParam(value = "longitude", required = false) BigDecimal lng) {
        MultipartFile upload = image != null ? image : file;
        RecognitionHistory result = recognitionService.recognize(LoginUserUtil.getCurrentUserId(), upload, source,
                location != null ? location : locationName, lat, lng);
        return Result.ok(recognitionService.toRecognitionResponse(result));
    }

    @Operation(summary = "识别记录列表")
    @GetMapping({"/recognitions", "/history", "/recognitions/history"})
    public Result<PageResponse<RecognitionResponse>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "q", required = false) String q) {
        int realSize = size != null ? size : pageSize;
        String realKeyword = keyword != null ? keyword : q;
        return Result.ok(PageResponse.from(recognitionService.pageRecognitionResults(
                LoginUserUtil.getCurrentUserId(), page, realSize, realKeyword)));
    }

    @Operation(summary = "识别记录详情")
    @GetMapping({"/recognitions/{id}", "/history/{id}", "/recognitions/history/{id}"})
    public Result<RecognitionResponse> historyDetail(@PathVariable Long id) {
        return Result.ok(recognitionService.getRecognitionResult(LoginUserUtil.getCurrentUserId(), id));
    }

    @Operation(summary = "编辑识别记录")
    @PatchMapping({"/recognitions/{id}", "/history/{id}", "/recognitions/history/{id}"})
    public Result<Void> editHistory(@PathVariable Long id, @RequestBody EditHistoryRequest req) {
        recognitionService.editHistory(LoginUserUtil.getCurrentUserId(), id, req);
        return Result.ok();
    }

    @Operation(summary = "删除识别记录")
    @DeleteMapping({"/recognitions/{id}", "/history/{id}", "/recognitions/history/{id}"})
    public Result<Void> deleteHistory(@PathVariable Long id) {
        recognitionService.deleteHistory(LoginUserUtil.getCurrentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "批量删除历史")
    @DeleteMapping({"/history/batch", "/recognitions/history/batch"})
    public Result<Map<String, Integer>> batchDelete(@RequestBody Map<String, List<Long>> body) {
        int count = recognitionService.batchDelete(LoginUserUtil.getCurrentUserId(), body.get("ids"));
        return Result.ok(Map.of("deletedCount", count));
    }
}
