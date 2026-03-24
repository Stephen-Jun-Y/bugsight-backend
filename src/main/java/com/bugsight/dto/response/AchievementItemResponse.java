package com.bugsight.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AchievementItemResponse {

    private Integer id;
    private String name;
    private String description;
    private String icon;
    private String conditionType;
    private Integer targetValue;
    private long currentValue;
    private int progressPercent;
    private boolean unlocked;
    private LocalDateTime unlockedAt;
}
