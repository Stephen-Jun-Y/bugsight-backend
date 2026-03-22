package com.bugsight.service;

import com.bugsight.dto.response.LocalizedTextResponse;
import com.bugsight.dto.response.SpeciesDetailResponse;
import com.bugsight.entity.InsectInfo;

import java.util.List;

public final class SpeciesResponseAssembler {

    private SpeciesResponseAssembler() {
    }

    public static SpeciesDetailResponse toResponse(InsectInfo insect) {
        if (insect == null) {
            return null;
        }

        return SpeciesDetailResponse.builder()
                .id(insect.getId())
                .speciesNameCn(insect.getSpeciesNameCn())
                .speciesNameEn(insect.getSpeciesNameEn())
                .orderName(insect.getOrderName())
                .orderNameCn(insect.getOrderNameCn())
                .familyName(insect.getFamilyName())
                .familyNameCn(insect.getFamilyNameCn())
                .genusName(insect.getGenusName())
                .genusNameCn(insect.getGenusNameCn())
                .bodyLength(insect.getBodyLength())
                .bodyLengthEn(insect.getBodyLengthEn())
                .distribution(insect.getDistribution())
                .distributionEn(insect.getDistributionEn())
                .activeSeason(insect.getActiveSeason())
                .activeSeasonEn(insect.getActiveSeasonEn())
                .protectionLevel(insect.getProtectionLevel())
                .protectionLevelEn(insect.getProtectionLevelEn())
                .harmLevel(insect.getHarmLevel())
                .description(insect.getDescription())
                .descriptionEn(insect.getDescriptionEn())
                .morphology(insect.getMorphology())
                .morphologyEn(insect.getMorphologyEn())
                .habits(insect.getHabits())
                .habitsEn(insect.getHabitsEn())
                .recognitionCount(insect.getRecognitionCount())
                .coverImageUrl(insect.getCoverImageUrl())
                .createdAt(insect.getCreatedAt())
                .updatedAt(insect.getUpdatedAt())
                .i18n(SpeciesDetailResponse.I18nPayload.builder()
                        .orderName(localized(insect.getOrderNameCn(), insect.getOrderName()))
                        .familyName(localized(insect.getFamilyNameCn(), insect.getFamilyName()))
                        .genusName(localized(insect.getGenusNameCn(), insect.getGenusName()))
                        .bodyLength(localized(insect.getBodyLength(), insect.getBodyLengthEn()))
                        .distribution(localized(insect.getDistribution(), insect.getDistributionEn()))
                        .activeSeason(localized(insect.getActiveSeason(), insect.getActiveSeasonEn()))
                        .protectionLevel(localized(insect.getProtectionLevel(), insect.getProtectionLevelEn()))
                        .description(localized(insect.getDescription(), insect.getDescriptionEn()))
                        .morphology(localized(insect.getMorphology(), insect.getMorphologyEn()))
                        .habits(localized(insect.getHabits(), insect.getHabitsEn()))
                        .build())
                .build();
    }

    public static List<SpeciesDetailResponse> toResponses(List<InsectInfo> insects) {
        return insects.stream().map(SpeciesResponseAssembler::toResponse).toList();
    }

    private static LocalizedTextResponse localized(String cn, String en) {
        return LocalizedTextResponse.builder()
                .cn(blankToNull(cn))
                .en(blankToNull(en))
                .build();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
