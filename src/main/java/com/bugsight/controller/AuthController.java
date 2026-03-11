package com.bugsight.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bugsight.common.result.Result;
import com.bugsight.common.utils.LoginUserUtil;
import com.bugsight.dto.request.EditProfileRequest;
import com.bugsight.dto.request.LoginRequest;
import com.bugsight.dto.request.RegisterRequest;
import com.bugsight.dto.response.AuthTokenResponse;
import com.bugsight.entity.User;
import com.bugsight.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "认证模块")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = authService.register(req);
        StpUtil.login(user.getId());
        return Result.ok(buildTokenResponse(user, authService.getToken()));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<AuthTokenResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = authService.login(req);
        return Result.ok(buildTokenResponse(user, authService.getToken()));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.ok();
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/profile")
    public Result<User> profile() {
        return Result.ok(authService.getProfile(LoginUserUtil.getCurrentUserId()));
    }

    @Operation(summary = "修改个人资料")
    @PutMapping("/profile")
    public Result<User> editProfile(@Valid @RequestBody EditProfileRequest req) {
        return Result.ok(authService.editProfile(LoginUserUtil.getCurrentUserId(), req));
    }

    private AuthTokenResponse buildTokenResponse(User user, String token) {
        Map<String, Object> userInfo = Map.of(
                "id", user.getId(),
                "nickname", user.getUsername(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
        );
        return AuthTokenResponse.builder()
                .accessToken(token)
                .refreshToken(token)
                .token(token)
                .userId(user.getId())
                .nickname(user.getUsername())
                .avatarUrl(user.getAvatarUrl() != null ? user.getAvatarUrl() : "")
                .user(userInfo)
                .build();
    }
}
