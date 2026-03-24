package com.bugsight.service;

import com.bugsight.dto.response.UserAchievementsResponse;
import com.bugsight.entity.Achievement;
import com.bugsight.entity.Post;
import com.bugsight.entity.RecognitionHistory;
import com.bugsight.entity.UserAchievement;
import com.bugsight.mapper.AchievementMapper;
import com.bugsight.mapper.PostMapper;
import com.bugsight.mapper.RecognitionHistoryMapper;
import com.bugsight.mapper.UserAchievementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private AchievementMapper achievementMapper;

    @Mock
    private UserAchievementMapper userAchievementMapper;

    @Mock
    private RecognitionHistoryMapper recognitionHistoryMapper;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private AchievementService achievementService;

    @Test
    void computesProgressAndUnlocksNewAchievements() {
        Achievement firstRecognition = achievement("初次探索", "recognition_count", 1, 1);
        Achievement explorer = achievement("探索者", "location_count", 3, 2);

        RecognitionHistory first = new RecognitionHistory();
        first.setTop1InsectId(69);
        first.setLocationName("上海");
        RecognitionHistory second = new RecognitionHistory();
        second.setTop1InsectId(70);
        second.setLocationName("杭州");

        when(achievementMapper.selectList(any())).thenReturn(List.of(firstRecognition, explorer));
        when(recognitionHistoryMapper.selectList(any())).thenReturn(List.of(first, second));
        when(postMapper.selectList(any())).thenReturn(List.of());
        when(userAchievementMapper.selectList(any())).thenReturn(List.of());

        UserAchievementsResponse response = achievementService.getUserAchievements(7L);

        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getUnlockedCount());
        assertTrue(response.getItems().get(0).isUnlocked());
        assertEquals(2L, response.getItems().get(1).getCurrentValue());
        assertEquals(67, response.getItems().get(1).getProgressPercent());
        assertFalse(response.getItems().get(1).isUnlocked());

        ArgumentCaptor<UserAchievement> captor = ArgumentCaptor.forClass(UserAchievement.class);
        verify(userAchievementMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(1, captor.getValue().getAchievementId());
        assertNotNull(captor.getValue().getAchievedAt());
    }

    @Test
    void keepsExistingUnlockRecordWithoutDuplicatingInsert() {
        Achievement communityStar = achievement("社区新星", "post_count", 1, 5);
        UserAchievement unlocked = new UserAchievement();
        unlocked.setAchievementId(5);
        unlocked.setAchievedAt(LocalDateTime.of(2026, 3, 20, 12, 0));
        Post post = new Post();
        post.setLikeCount(0);

        when(achievementMapper.selectList(any())).thenReturn(List.of(communityStar));
        when(recognitionHistoryMapper.selectList(any())).thenReturn(List.of());
        when(postMapper.selectList(any())).thenReturn(List.of(post));
        when(userAchievementMapper.selectList(any())).thenReturn(List.of(unlocked));

        UserAchievementsResponse response = achievementService.getUserAchievements(7L);

        assertTrue(response.getItems().get(0).isUnlocked());
        assertEquals(LocalDateTime.of(2026, 3, 20, 12, 0), response.getItems().get(0).getUnlockedAt());
        verify(userAchievementMapper, never()).insert(org.mockito.ArgumentMatchers.any(UserAchievement.class));
    }

    private Achievement achievement(String name, String conditionType, int conditionValue, int id) {
        Achievement achievement = new Achievement();
        achievement.setId(id);
        achievement.setName(name);
        achievement.setDescription(name + "说明");
        achievement.setIcon("🏅");
        achievement.setConditionType(conditionType);
        achievement.setConditionValue(conditionValue);
        return achievement;
    }
}
