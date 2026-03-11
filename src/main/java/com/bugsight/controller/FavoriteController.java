package com.bugsight.controller;

import com.bugsight.common.result.Result;
import com.bugsight.common.utils.LoginUserUtil;
import com.bugsight.dto.response.PageResponse;
import com.bugsight.entity.InsectInfo;
import com.bugsight.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "收藏模块")
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏列表")
    @GetMapping
    public Result<PageResponse<InsectInfo>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "size", required = false) Integer size) {
        int realSize = size != null ? size : pageSize;
        return Result.ok(PageResponse.from(favoriteService.listFavorites(LoginUserUtil.getCurrentUserId(), page, realSize)));
    }

    @Operation(summary = "添加收藏")
    @PostMapping("/{insectId}")
    public Result<Void> add(@PathVariable Integer insectId) {
        favoriteService.addFavorite(LoginUserUtil.getCurrentUserId(), insectId);
        return Result.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{insectId}")
    public Result<Void> remove(@PathVariable Integer insectId) {
        favoriteService.removeFavorite(LoginUserUtil.getCurrentUserId(), insectId);
        return Result.ok();
    }

    @Operation(summary = "切换收藏")
    @PostMapping("/{insectId}/toggle")
    public Result<Map<String, Boolean>> toggle(@PathVariable Integer insectId) {
        boolean isFavorited = favoriteService.toggleFavorite(LoginUserUtil.getCurrentUserId(), insectId);
        return Result.ok(Map.of("isFavorited", isFavorited));
    }

    @Operation(summary = "是否已收藏")
    @GetMapping("/{insectId}/status")
    public Result<Map<String, Boolean>> status(@PathVariable Integer insectId) {
        boolean isFavorited = favoriteService.isFavorited(LoginUserUtil.getCurrentUserId(), insectId);
        return Result.ok(Map.of("isFavorited", isFavorited));
    }
}
