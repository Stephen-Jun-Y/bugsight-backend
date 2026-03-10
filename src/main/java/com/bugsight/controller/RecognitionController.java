package com.bugsight.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bugsight.common.result.Result;
import com.bugsight.common.utils.LoginUserUtil;
import com.bugsight.dto.request.EditHistoryRequest;
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
    @PostMapping("/recognize")
    public Result<RecognitionHistory> recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source", defaultValue = "1") Integer source,
            @RequestParam(value = "locationName", required = false) String locationName,
            @RequestParam(value = "latitude", required = false) BigDecimal lat,
            @RequestParam(value = "longitude", required = false) BigDecimal lng) {
        Long userId = LoginUserUtil.getCurrentUserId();
        RecognitionHistory result = recognitionService.recognize(userId, file, source, locationName, lat, lng);
        return Result.ok(result);
    }

    @Operation(summary = "历史记录列表（分页）")
    @GetMapping("/history")
    public Result<Page<RecognitionHistory>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        return Result.ok(recognitionService.pageHistory(LoginUserUtil.getCurrentUserId(), page, size, q));
    }

    @Operation(summary = "历史记录详情")
    @GetMapping("/history/{id}")
    public Result<RecognitionHistory> historyDetail(@PathVariable Long id) {
        return Result.ok(recognitionService.getDetail(LoginUserUtil.getCurrentUserId(), id));
    }

    @Operation(summary = "编辑历史记录")
    @PutMapping("/history/{id}")
    public Result<Void> editHistory(@PathVariable Long id, @RequestBody EditHistoryRequest req) {
        recognitionService.editHistory(LoginUserUtil.getCurrentUserId(), id, req);
        return Result.ok();
    }

    @Operation(summary = "删除单条历史")
    @DeleteMapping("/history/{id}")
    public Result<Void> deleteHistory(@PathVariable Long id) {
        recognitionService.deleteHistory(LoginUserUtil.getCurrentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "批量删除历史")
    @DeleteMapping("/history/batch")
    public Result<Map<String, Integer>> batchDelete(@RequestBody Map<String, List<Long>> body) {
        int count = recognitionService.batchDelete(LoginUserUtil.getCurrentUserId(), body.get("ids"));
        return Result.ok(Map.of("deletedCount", count));
    }
}
