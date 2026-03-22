package com.bugsight.controller;

import com.bugsight.common.result.Result;
import com.bugsight.common.utils.LoginUserUtil;
import com.bugsight.dto.request.ChangePasswordRequest;
import com.bugsight.dto.request.UpdateMeRequest;
import com.bugsight.dto.response.PageResponse;
import com.bugsight.dto.response.PublicUserProfileResponse;
import com.bugsight.dto.response.UserProfileResponse;
import com.bugsight.entity.InsectInfo;
import com.bugsight.entity.Post;
import com.bugsight.service.AuthService;
import com.bugsight.service.FavoriteService;
import com.bugsight.service.PostService;
import com.bugsight.service.UserProfileService;
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
    private final UserProfileService userProfileService;
    private final PostService postService;
    private final FavoriteService favoriteService;

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

    @Operation(summary = "公开用户主页")
    @GetMapping("/{id}/profile")
    public Result<PublicUserProfileResponse> getPublicProfile(@PathVariable Long id) {
        Long currentUserId = LoginUserUtil.isLogin() ? LoginUserUtil.getCurrentUserId() : null;
        return Result.ok(userProfileService.getPublicProfile(id, currentUserId));
    }

    @Operation(summary = "公开用户动态列表")
    @GetMapping("/{id}/posts")
    public Result<PageResponse<Post>> listUserPosts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "size", required = false) Integer size) {
        userProfileService.assertUserExists(id);
        Long currentUserId = LoginUserUtil.isLogin() ? LoginUserUtil.getCurrentUserId() : null;
        int realSize = size != null ? size : pageSize;
        return Result.ok(PageResponse.from(postService.listUserPosts(id, currentUserId, page, realSize)));
    }

    @Operation(summary = "公开用户收藏列表")
    @GetMapping("/{id}/favorites")
    public Result<PageResponse<InsectInfo>> listUserFavorites(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "size", required = false) Integer size) {
        userProfileService.assertUserExists(id);
        int realSize = size != null ? size : pageSize;
        return Result.ok(PageResponse.from(favoriteService.listFavoritesByUser(id, page, realSize)));
    }

    @Operation(summary = "关注状态")
    @GetMapping("/{id}/follow-status")
    public Result<Map<String, Boolean>> followStatus(@PathVariable Long id) {
        Long currentUserId = LoginUserUtil.isLogin() ? LoginUserUtil.getCurrentUserId() : null;
        return Result.ok(Map.of("isFollowing", userProfileService.getFollowStatus(id, currentUserId)));
    }

    @Operation(summary = "关注用户")
    @PostMapping("/{id}/follow")
    public Result<Map<String, Boolean>> follow(@PathVariable Long id) {
        return Result.ok(Map.of("isFollowing", userProfileService.followUser(LoginUserUtil.getCurrentUserId(), id)));
    }

    @Operation(summary = "取消关注用户")
    @DeleteMapping("/{id}/follow")
    public Result<Map<String, Boolean>> unfollow(@PathVariable Long id) {
        return Result.ok(Map.of("isFollowing", userProfileService.unfollowUser(LoginUserUtil.getCurrentUserId(), id)));
    }
}
