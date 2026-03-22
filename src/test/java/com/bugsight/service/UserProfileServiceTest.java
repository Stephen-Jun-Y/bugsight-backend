package com.bugsight.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bugsight.dto.response.PublicUserProfileResponse;
import com.bugsight.entity.Favorite;
import com.bugsight.entity.Post;
import com.bugsight.entity.RecognitionHistory;
import com.bugsight.entity.User;
import com.bugsight.entity.UserFollow;
import com.bugsight.mapper.FavoriteMapper;
import com.bugsight.mapper.NotificationMapper;
import com.bugsight.mapper.PostMapper;
import com.bugsight.mapper.RecognitionHistoryMapper;
import com.bugsight.mapper.UserFollowMapper;
import com.bugsight.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, UserFollow.class);
        TableInfoHelper.initTableInfo(assistant, Post.class);
        TableInfoHelper.initTableInfo(assistant, Favorite.class);
        TableInfoHelper.initTableInfo(assistant, RecognitionHistory.class);
    }

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserFollowMapper userFollowMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private RecognitionHistoryMapper recognitionHistoryMapper;
    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void buildsPublicProfileStatsAndFollowFlags() {
        User user = new User();
        user.setId(7L);
        user.setUsername("昆虫猎人");
        user.setBio("常驻野外观察昆虫");

        Post first = new Post();
        first.setLikeCount(3);
        Post second = new Post();
        second.setLikeCount(5);

        when(userMapper.selectById(7L)).thenReturn(user);
        when(recognitionHistoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(328L);
        when(postMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(56L);
        when(postMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(12L);
        when(userFollowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L, 89L, 24L);

        PublicUserProfileResponse response = userProfileService.getPublicProfile(7L, 9L);

        assertEquals(7L, response.getId());
        assertEquals("昆虫猎人", response.getNickname());
        assertTrue(response.getIsFollowing());
        assertFalse(response.getIsSelf());
        assertEquals(328L, response.getRecognitionCount());
        assertEquals(56L, response.getPostCount());
        assertEquals(8L, response.getReceivedLikeCount());
        assertEquals(12L, response.getFavoriteCount());
        assertEquals(89L, response.getFollowerCount());
        assertEquals(24L, response.getFollowingCount());
    }

    @Test
    void followStatusDefaultsToFalseForAnonymousViewer() {
        assertFalse(userProfileService.getFollowStatus(7L, null));
        verify(userFollowMapper, never()).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    void followAndUnfollowReturnCurrentState() {
        User user = new User();
        user.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userFollowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 1L);

        assertTrue(userProfileService.followUser(9L, 7L));
        assertFalse(userProfileService.unfollowUser(9L, 7L));
    }
}
