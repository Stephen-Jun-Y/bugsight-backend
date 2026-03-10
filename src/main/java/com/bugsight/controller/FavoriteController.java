package com.bugsight.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bugsight.common.result.Result;
import com.bugsight.common.utils.LoginUserUtil;
import com.bugsight.entity.InsectInfo;
import com.bugsight.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "收藏模块")
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏列表")
    @GetMapping
    public Result<Page<InsectInfo>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(favoriteService.listFavorites(LoginUserUtil.getCurrentUserId(), page, size));
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
}
