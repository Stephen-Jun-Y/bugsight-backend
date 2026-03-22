package com.bugsight.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SpeciesDetailResponse {

    private Integer id;
    private String speciesNameCn;
    private String speciesNameEn;
    private String orderName;
    private String orderNameCn;
    private String familyName;
    private String familyNameCn;
    private String genusName;
    private String genusNameCn;
    private String bodyLength;
    private String bodyLengthEn;
    private String distribution;
    private String distributionEn;
    private String activeSeason;
    private String activeSeasonEn;
    private String protectionLevel;
    private String protectionLevelEn;
    private Integer harmLevel;
    private String description;
    private String descriptionEn;
    private String morphology;
    private String morphologyEn;
    private String habits;
    private String habitsEn;
    private Integer recognitionCount;
    private String coverImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private I18nPayload i18n;

    @Data
    @Builder
    public static class I18nPayload {
        private LocalizedTextResponse orderName;
        private LocalizedTextResponse familyName;
        private LocalizedTextResponse genusName;
        private LocalizedTextResponse bodyLength;
        private LocalizedTextResponse distribution;
        private LocalizedTextResponse activeSeason;
        private LocalizedTextResponse protectionLevel;
        private LocalizedTextResponse description;
        private LocalizedTextResponse morphology;
        private LocalizedTextResponse habits;
    }
}
