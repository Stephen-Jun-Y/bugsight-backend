package com.bugsight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bugsight.common.exception.BusinessException;
import com.bugsight.common.result.ResultCode;
import com.bugsight.dto.response.PublicUserProfileResponse;
import com.bugsight.entity.Favorite;
import com.bugsight.entity.Post;
import com.bugsight.entity.RecognitionHistory;
import com.bugsight.entity.User;
import com.bugsight.entity.UserFollow;
import com.bugsight.mapper.FavoriteMapper;
import com.bugsight.mapper.PostMapper;
import com.bugsight.mapper.RecognitionHistoryMapper;
import com.bugsight.mapper.UserFollowMapper;
import com.bugsight.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final PostMapper postMapper;
    private final FavoriteMapper favoriteMapper;
    private final RecognitionHistoryMapper recognitionHistoryMapper;

    public PublicUserProfileResponse getPublicProfile(Long targetUserId, Long currentUserId) {
        User user = requireUser(targetUserId);
        boolean isSelf = currentUserId != null && currentUserId.equals(targetUserId);

        long recognitionCount = recognitionHistoryMapper.selectCount(new LambdaQueryWrapper<RecognitionHistory>()
                .eq(RecognitionHistory::getUserId, targetUserId));
        long postCount = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, targetUserId)
                .eq(Post::getVisibility, 1));
        long receivedLikeCount = postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, targetUserId)
                        .eq(Post::getVisibility, 1)
                        .select(Post::getLikeCount))
                .stream()
                .map(Post::getLikeCount)
                .filter(count -> count != null)
                .mapToLong(Integer::longValue)
                .sum();
        long favoriteCount = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, targetUserId));
        boolean isFollowing = !isSelf && getFollowStatus(targetUserId, currentUserId);
        long followerCount = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowingId, targetUserId));
        long followingCount = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, targetUserId));

        return PublicUserProfileResponse.builder()
                .id(user.getId())
                .nickname(user.getUsername())
                .bio(defaultText(user.getBio()))
                .avatarUrl(defaultText(user.getAvatarUrl()))
                .location("")
                .isFollowing(isFollowing)
                .isSelf(isSelf)
                .recognitionCount(recognitionCount)
                .postCount(postCount)
                .receivedLikeCount(receivedLikeCount)
                .favoriteCount(favoriteCount)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .build();
    }

    public void assertUserExists(Long targetUserId) {
        requireUser(targetUserId);
    }

    public boolean getFollowStatus(Long targetUserId, Long currentUserId) {
        if (currentUserId == null || currentUserId.equals(targetUserId)) {
            return false;
        }

        return userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFollowingId, targetUserId)) > 0;
    }

    @Transactional
    public boolean followUser(Long currentUserId, Long targetUserId) {
        validateFollowTarget(currentUserId, targetUserId);
        if (!getFollowStatus(targetUserId, currentUserId)) {
            UserFollow follow = new UserFollow();
            follow.setFollowerId(currentUserId);
            follow.setFollowingId(targetUserId);
            userFollowMapper.insert(follow);
        }
        return true;
    }

    @Transactional
    public boolean unfollowUser(Long currentUserId, Long targetUserId) {
        validateFollowTarget(currentUserId, targetUserId);
        userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFollowingId, targetUserId));
        return false;
    }

    private void validateFollowTarget(Long currentUserId, Long targetUserId) {
        requireUser(targetUserId);
        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "不能关注自己");
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }
}
