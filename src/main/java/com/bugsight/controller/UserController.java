package com.bugsight.controller;

import com.bugsight.common.result.Result;
import com.bugsight.common.utils.LoginUserUtil;
import com.bugsight.dto.request.ChangePasswordRequest;
import com.bugsight.dto.request.UpdateMeRequest;
import com.bugsight.dto.response.UserProfileResponse;
import com.bugsight.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "用户模块")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<UserProfileResponse> me() {
        return Result.ok(authService.getCurrentUserProfile(LoginUserUtil.getCurrentUserId()));
    }

    @Operation(summary = "更新个人资料")
    @PatchMapping("/me")
    public Result<UserProfileResponse> updateMe(@Valid @RequestBody UpdateMeRequest req) {
        return Result.ok(authService.updateCurrentUserProfile(LoginUserUtil.getCurrentUserId(), req));
    }

    @Operation(summary = "修改密码")
    @PostMapping("/me/password")
    public Result<Map<String, Boolean>> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(LoginUserUtil.getCurrentUserId(), req);
        return Result.ok(Map.of("success", true));
    }

    @Operation(summary = "注销账号")
    @DeleteMapping("/me")
    public Result<Void> deleteMe() {
        authService.deleteCurrentUser(LoginUserUtil.getCurrentUserId());
        return Result.ok();
    }
}
