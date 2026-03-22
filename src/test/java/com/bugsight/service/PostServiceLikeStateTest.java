package com.bugsight.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.bugsight.entity.Post;
import com.bugsight.entity.PostComment;
import com.bugsight.entity.PostLike;
import com.bugsight.entity.User;
import com.bugsight.mapper.NotificationMapper;
import com.bugsight.mapper.PostCommentMapper;
import com.bugsight.mapper.PostLikeMapper;
import com.bugsight.mapper.PostMapper;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceLikeStateTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PostLike.class);
    }

    @Mock
    private PostMapper postMapper;
    @Mock
    private PostCommentMapper commentMapper;
    @Mock
    private PostLikeMapper likeMapper;
    @Mock
    private UserFollowMapper followMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FileService fileService;

    @InjectMocks
    private PostService postService;

    @Test
    void marksLikedPostsForCurrentUserInListResponses() {
        Post post = new Post();
        post.setId(21L);
        Page<Post> page = new Page<>(1, 20);
        page.setRecords(List.of(post));

        PostLike like = new PostLike();
        like.setPostId(21L);

        when(postMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(likeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(like));

        Page<Post> result = postService.listPosts("recommend", 7L, 1, 20);

        assertTrue(Boolean.TRUE.equals(result.getRecords().get(0).getLikedByCurrentUser()));
    }

    @Test
    void skipsLikeLookupForAnonymousDetailRequests() {
        Post post = new Post();
        post.setId(22L);

        when(postMapper.selectById(22L)).thenReturn(post);

        Post result = postService.getPostDetail(22L, null);

        assertFalse(Boolean.TRUE.equals(result.getLikedByCurrentUser()));
        verify(likeMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }
    @Test
    void hydratesAuthorNicknameAndAvatarForPostsAndComments() {
        Post post = new Post();
        post.setId(33L);
        post.setUserId(8L);

        PostComment comment = new PostComment();
        comment.setId(44L);
        comment.setUserId(5L);

        Page<Post> postPage = new Page<>(1, 20);
        postPage.setRecords(List.of(post));
        Page<PostComment> commentPage = new Page<>(1, 20);
        commentPage.setRecords(List.of(comment));

        User author = new User();
        author.setId(8L);
        author.setUsername("叶蝉观察员");
        author.setAvatarUrl("https://example.com/avatar-8.jpg");
        User commenter = new User();
        commenter.setId(5L);
        commenter.setUsername("评论区伙伴");
        commenter.setAvatarUrl("https://example.com/avatar-5.jpg");

        when(postMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(postPage);
        when(commentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(commentPage);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(author, commenter));
        when(likeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Page<Post> posts = postService.listPosts("recommend", 7L, 1, 20);
        Page<PostComment> comments = postService.getComments(33L, 1, 20);

        assertTrue("叶蝉观察员".equals(posts.getRecords().get(0).getAuthorNickname()));
        assertTrue("https://example.com/avatar-8.jpg".equals(posts.getRecords().get(0).getAuthorAvatarUrl()));
        assertTrue("评论区伙伴".equals(comments.getRecords().get(0).getAuthorNickname()));
        assertTrue("https://example.com/avatar-5.jpg".equals(comments.getRecords().get(0).getAuthorAvatarUrl()));
    }

}
