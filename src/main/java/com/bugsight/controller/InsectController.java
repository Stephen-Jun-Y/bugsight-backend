package com.bugsight.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bugsight.common.result.Result;
import com.bugsight.entity.InsectInfo;
import com.bugsight.service.InsectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "昆虫信息模块")
@RestController
@RequestMapping("/insects")
@RequiredArgsConstructor
public class InsectController {

    private final InsectService insectService;

    @Operation(summary = "热门昆虫（首页）")
    @GetMapping("/popular")
    public Result<List<InsectInfo>> popular(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(insectService.getPopular(limit));
    }

    @Operation(summary = "搜索昆虫")
    @GetMapping("/search")
    public Result<Page<InsectInfo>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer harmLevel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(insectService.search(q, harmLevel, page, size));
    }

    @Operation(summary = "昆虫详情")
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
