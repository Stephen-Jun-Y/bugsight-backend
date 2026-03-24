package com.bugsight.controller;

import com.bugsight.common.result.Result;
import com.bugsight.dto.response.PageResponse;
import com.bugsight.dto.response.SpeciesDetailResponse;
import com.bugsight.entity.InsectInfo;
import com.bugsight.service.InsectService;
import com.bugsight.service.SearchKeywordStatService;
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
    private final SearchKeywordStatService searchKeywordStatService;

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

    @Operation(summary = "热门搜索关键词")
    @GetMapping("/hot-searches")
    public Result<List<SearchKeywordStatService.HotSearchItem>> hotSearches(
            @RequestParam(defaultValue = "8") int limit) {
        return Result.ok(searchKeywordStatService.listHotSearches(limit));
    }

    @Operation(summary = "物种详情")
    @GetMapping("/{id}")
    public Result<SpeciesDetailResponse> detail(@PathVariable Integer id) {
        return Result.ok(com.bugsight.service.SpeciesResponseAssembler.toResponse(insectService.getById(id)));
    }

    @Operation(summary = "相似物种")
    @GetMapping("/{id}/similar")
    public Result<List<SpeciesDetailResponse>> similar(@PathVariable Integer id) {
        return Result.ok(com.bugsight.service.SpeciesResponseAssembler.toResponses(insectService.getSimilar(id)));
    }
}
