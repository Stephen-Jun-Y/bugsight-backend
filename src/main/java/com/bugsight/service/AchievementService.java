package com.bugsight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bugsight.dto.response.AchievementItemResponse;
import com.bugsight.dto.response.UserAchievementsResponse;
import com.bugsight.entity.Achievement;
import com.bugsight.entity.Post;
import com.bugsight.entity.RecognitionHistory;
import com.bugsight.entity.UserAchievement;
import com.bugsight.mapper.AchievementMapper;
import com.bugsight.mapper.PostMapper;
import com.bugsight.mapper.RecognitionHistoryMapper;
import com.bugsight.mapper.UserAchievementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final RecognitionHistoryMapper recognitionHistoryMapper;
    private final PostMapper postMapper;

    public UserAchievementsResponse getUserAchievements(Long userId) {
        List<Achievement> definitions = achievementMapper.selectList(new LambdaQueryWrapper<Achievement>()
                .orderByAsc(Achievement::getId));

        List<RecognitionHistory> histories = recognitionHistoryMapper.selectList(new LambdaQueryWrapper<RecognitionHistory>()
                .eq(RecognitionHistory::getUserId, userId));
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId));
        Map<Integer, UserAchievement> unlockedMap = userAchievementMapper.selectList(new LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementId, Function.identity(), (left, right) -> left));

        Metrics metrics = buildMetrics(histories, posts);
        List<AchievementItemResponse> items = definitions.stream()
                .map(definition -> toResponse(definition, metrics, unlockedMap, userId))
                .toList();

        int unlockedCount = (int) items.stream().filter(AchievementItemResponse::isUnlocked).count();
        return UserAchievementsResponse.builder()
                .unlockedCount(unlockedCount)
                .totalCount(items.size())
                .items(items)
                .build();
    }

    private AchievementItemResponse toResponse(
            Achievement definition,
            Metrics metrics,
            Map<Integer, UserAchievement> unlockedMap,
            Long userId
    ) {
        long currentValue = metrics.valueOf(definition.getConditionType());
        int targetValue = definition.getConditionValue() == null ? 0 : definition.getConditionValue();
        UserAchievement existing = unlockedMap.get(definition.getId());
        LocalDateTime unlockedAt = existing != null ? existing.getAchievedAt() : null;
        boolean unlocked = existing != null || (targetValue > 0 && currentValue >= targetValue);

        if (unlocked && existing == null) {
            UserAchievement created = new UserAchievement();
            created.setUserId(userId);
            created.setAchievementId(definition.getId());
            created.setAchievedAt(LocalDateTime.now());
            userAchievementMapper.insert(created);
            unlockedAt = created.getAchievedAt();
        }

        int progressPercent = targetValue <= 0
                ? (unlocked ? 100 : 0)
                : (int) Math.min(100, Math.round((currentValue * 100.0) / targetValue));

        return AchievementItemResponse.builder()
                .id(definition.getId())
                .name(definition.getName())
                .description(definition.getDescription())
                .icon(definition.getIcon())
                .conditionType(definition.getConditionType())
                .targetValue(targetValue)
                .currentValue(currentValue)
                .progressPercent(progressPercent)
                .unlocked(unlocked)
                .unlockedAt(unlockedAt)
                .build();
    }

    private Metrics buildMetrics(List<RecognitionHistory> histories, List<Post> posts) {
        long recognitionCount = histories.size();
        long speciesCount = histories.stream()
                .map(RecognitionHistory::getTop1InsectId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        long locationCount = histories.stream()
                .map(RecognitionHistory::getLocationName)
                .filter(location -> location != null && !location.isBlank())
                .map(String::trim)
                .distinct()
                .count();
        long postCount = posts.size();
        long likeReceived = posts.stream()
                .map(Post::getLikeCount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        return new Metrics(recognitionCount, speciesCount, locationCount, postCount, likeReceived);
    }

    private record Metrics(
            long recognitionCount,
            long speciesCount,
            long locationCount,
            long postCount,
            long likeReceived
    ) {
        long valueOf(String conditionType) {
            return switch (conditionType == null ? "" : conditionType) {
                case "recognition_count" -> recognitionCount;
                case "species_count" -> speciesCount;
                case "location_count" -> locationCount;
                case "post_count" -> postCount;
                case "like_received" -> likeReceived;
                default -> 0L;
            };
        }
    }
}
