package com.bugsight.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bugsight.common.result.Result;
import com.bugsight.common.utils.LoginUserUtil;
import com.bugsight.dto.request.CreatePostRequest;
import com.bugsight.entity.Post;
import com.bugsight.entity.PostComment;
import com.bugsight.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "社区模块")
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "发布动态")
    @PostMapping
    public Result<Post> create(
            @Valid @RequestPart("data") CreatePostRequest req,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return Result.ok(postService.createPost(LoginUserUtil.getCurrentUserId(), req, image));
    }

    @Operation(summary = "动态列表")
    @GetMapping
    public Result<Page<Post>> list(
            @RequestParam(defaultValue = "recommend") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = LoginUserUtil.isLogin() ? LoginUserUtil.getCurrentUserId() : null;
        return Result.ok(postService.listPosts(tab, userId, page, size));
    }

    @Operation(summary = "动态详情")
    @GetMapping("/{id}")
    public Result<Post> detail(@PathVariable Long id) {
        return Result.ok(postService.getPostById(id));
    }

    @Operation(summary = "点赞/取消点赞")
    @PostMapping("/{id}/like")
    public Result<Map<String, Boolean>> like(@PathVariable Long id) {
        boolean isLiked = postService.toggleLike(LoginUserUtil.getCurrentUserId(), id);
        return Result.ok(Map.of("isLiked", isLiked));
    }

    @Operation(summary = "评论列表")
    @GetMapping("/{id}/comments")
    public Result<Page<PostComment>> comments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(postService.getComments(id, page, size));
    }

    @Operation(summary = "发表评论")
    @PostMapping("/{id}/comments")
    public Result<PostComment> addComment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        Long parentId = body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null;
        return Result.ok(postService.addComment(LoginUserUtil.getCurrentUserId(), id, content, parentId));
    }

    @Operation(summary = "删除动态")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postService.deletePost(LoginUserUtil.getCurrentUserId(), id);
        return Result.ok();
    }
}
