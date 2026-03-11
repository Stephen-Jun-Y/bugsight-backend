package com.bugsight.controller;

import com.bugsight.common.result.Result;
import com.bugsight.dto.response.PageResponse;
import com.bugsight.entity.InsectInfo;
import com.bugsight.service.InsectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "物种模块")
@RestController
@RequestMapping("/species")
@RequiredArgsConstructor
public class SpeciesController {

    private final InsectService insectService;

    @Operation(summary = "物种搜索")
    @GetMapping("/search")
    public Result<PageResponse<InsectInfo>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer harmLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(PageResponse.from(insectService.search(q, harmLevel, page, size != null ? size : pageSize)));
    }

    @Operation(summary = "物种详情")
    @GetMapping("/{id}")
    public Result<InsectInfo> detail(@PathVariable Integer id) {
        return Result.ok(insectService.getById(id));
    }

    @Operation(summary = "相似物种")
    @GetMapping("/{id}/similar")
    public Result<List<InsectInfo>> similar(@PathVariable Integer id) {
        return Result.ok(insectService.getSimilar(id));
    }
}
