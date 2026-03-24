package com.bugsight.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserAchievementsResponse {

    private int unlockedCount;
    private int totalCount;
    private List<AchievementItemResponse> items;
}
