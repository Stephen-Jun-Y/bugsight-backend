package com.bugsight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bugsight.common.exception.BusinessException;
import com.bugsight.common.result.ResultCode;
import com.bugsight.dto.request.CreatePostRequest;
import com.bugsight.entity.*;
import com.bugsight.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final PostLikeMapper likeMapper; // need to create this mapper
    private final UserFollowMapper followMapper;
    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final FileService fileService;

    @Transactional
    public Post createPost(Long userId, CreatePostRequest req, MultipartFile image) {
        Post post = new Post();
        post.setUserId(userId);
        post.setContent(req.getContent());
        post.setTopicTags(req.getTopicTags());
        post.setLocationName(req.getLocationName());
        post.setLatitude(req.getLatitude());
        post.setLongitude(req.getLongitude());
        post.setVisibility(req.getVisibility() != null ? req.getVisibility() : 1);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setShareCount(0);

        if (image != null && !image.isEmpty()) {
            post.setImageUrl(fileService.saveImage(image));
        }
        postMapper.insert(post);
        applyAuthorInfo(List.of(post));
        return post;
    }

    public Page<Post> listPosts(String tab, Long currentUserId, int page, int size) {
        Page<Post> pageReq = new Page<>(page, size);
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getVisibility, 1)
                .orderByDesc(Post::getCreatedAt);

        if ("following".equals(tab) && currentUserId != null) {
            List<Long> followingIds = followMapper.selectList(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, currentUserId)
                            .select(UserFollow::getFollowingId))
                    .stream().map(UserFollow::getFollowingId).toList();
            if (followingIds.isEmpty()) return new Page<>(page, size);
            wrapper.in(Post::getUserId, followingIds);
        }
        Page<Post> result = postMapper.selectPage(pageReq, wrapper);
        applyAuthorInfo(result.getRecords());
        applyLikedState(result.getRecords(), currentUserId);
        return result;
    }

    public Page<Post> listUserPosts(Long targetUserId, Long currentUserId, int page, int size) {
        Page<Post> result = postMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, targetUserId)
                        .eq(Post::getVisibility, 1)
                        .orderByDesc(Post::getCreatedAt));
        applyAuthorInfo(result.getRecords());
        applyLikedState(result.getRecords(), currentUserId);
        return result;
    }

    public Post getPostById(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) throw new BusinessException(ResultCode.POST_NOT_FOUND);
        return post;
    }

    public Post getPostDetail(Long id, Long currentUserId) {
        Post post = getPostById(id);
        applyAuthorInfo(List.of(post));
        applyLikedState(List.of(post), currentUserId);
        return post;
    }

    @Transactional
    public boolean toggleLike(Long userId, Long postId) {
        Post post = getPostById(postId);
        LambdaQueryWrapper<PostLike> wrapper = new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId)
                .eq(PostLike::getPostId, postId);

        if (likeMapper.selectCount(wrapper) > 0) {
            likeMapper.delete(wrapper);
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("like_count = like_count - 1"));
            return false;
        } else {
            PostLike like = new PostLike();
            like.setUserId(userId);
            like.setPostId(postId);
            likeMapper.insert(like);
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("like_count = like_count + 1"));
            // 发送通知
            sendNotification(post.getUserId(), "like", userId, "post", postId,
                    "有人点赞了你的动态");
            return true;
        }
    }

    @Transactional
    public PostComment addComment(Long userId, Long postId, String content, Long parentId) {
        getPostById(postId); // 确认帖子存在
        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setParentId(parentId);
        comment.setLikeCount(0);
        commentMapper.insert(comment);

        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("comment_count = comment_count + 1"));
        applyAuthorInfoToComments(List.of(comment));
        return comment;
    }

    public Page<PostComment> getComments(Long postId, int page, int size) {
        Page<PostComment> result = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getPostId, postId)
                        .isNull(PostComment::getParentId)
                        .orderByAsc(PostComment::getCreatedAt));
        applyAuthorInfoToComments(result.getRecords());
        return result;
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = getPostById(postId);
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_POST_OWNER);
        }
        postMapper.deleteById(postId);
    }

    private void sendNotification(Long toUserId, String type, Long actorId,
                                   String targetType, Long targetId, String content) {
        if (toUserId.equals(actorId)) return; // 不给自己发通知
        Notification notification = new Notification();
        notification.setUserId(toUserId);
        notification.setType(type);
        notification.setActorId(actorId);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setContent(content);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
    }

    private void applyLikedState(List<Post> posts, Long currentUserId) {
        if (posts == null || posts.isEmpty()) {
            return;
        }

        if (currentUserId == null) {
            posts.forEach(post -> post.setLikedByCurrentUser(false));
            return;
        }

        List<Long> postIds = posts.stream()
                .map(Post::getId)
                .filter(id -> id != null)
                .toList();
        if (postIds.isEmpty()) {
            posts.forEach(post -> post.setLikedByCurrentUser(false));
            return;
        }

        Set<Long> likedPostIds = likeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getUserId, currentUserId)
                        .in(PostLike::getPostId, postIds)
                        .select(PostLike::getPostId))
                .stream()
                .map(PostLike::getPostId)
                .collect(Collectors.toSet());

        posts.forEach(post -> post.setLikedByCurrentUser(likedPostIds.contains(post.getId())));
    }

    private void applyAuthorInfo(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }

        List<Long> userIds = posts.stream()
                .map(Post::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }

        java.util.Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        posts.forEach(post -> {
            User author = userMap.get(post.getUserId());
            if (author != null) {
                post.setAuthorNickname(author.getUsername());
                post.setAuthorAvatarUrl(author.getAvatarUrl());
            }
        });
    }

    private void applyAuthorInfoToComments(List<PostComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }

        List<Long> userIds = comments.stream()
                .map(PostComment::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }

        java.util.Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        comments.forEach(comment -> {
            User author = userMap.get(comment.getUserId());
            if (author != null) {
                comment.setAuthorNickname(author.getUsername());
                comment.setAuthorAvatarUrl(author.getAvatarUrl());
            }
        });
    }
}
