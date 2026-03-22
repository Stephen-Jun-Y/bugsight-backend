package com.bugsight.service;

import com.bugsight.entity.InsectInfo;
import com.bugsight.mapper.InsectInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InsectCatalogService {

    private final InsectInfoMapper insectMapper;
    private final ModelLabelService modelLabelService;

    public InsectInfo getOrCreate(Integer id) {
        if (id == null) {
            return null;
        }

        InsectInfo existing = insectMapper.selectById(id);
        if (existing != null) {
            return existing;
        }

        return modelLabelService.getLabel(id)
                .map(this::buildFallback)
                .orElse(null);
    }

    public List<InsectInfo> listFallbackSimilar(Integer id, int limit) {
        return modelLabelService.listLabels().stream()
                .filter(label -> !label.classId().equals(id))
                .limit(limit)
                .map(this::buildFallback)
                .toList();
    }

    private InsectInfo buildFallback(ModelLabelService.LabelMeta label) {
        InsectInfo insect = new InsectInfo();
        insect.setId(label.classId());
        insect.setSpeciesNameCn(label.speciesNameCn());
        insect.setSpeciesNameEn(label.speciesNameEn());
        insect.setOrderName("IP102");
        insect.setOrderNameCn("待补充");
        insect.setFamilyName("IP102");
        insect.setFamilyNameCn("待补充");
        insect.setGenusName("To be added");
        insect.setGenusNameCn("待补充");
        insect.setBodyLength("待补充");
        insect.setBodyLengthEn("To be added.");
        insect.setDistribution("待补充");
        insect.setDistributionEn("To be added.");
        insect.setActiveSeason("待补充");
        insect.setActiveSeasonEn("To be added.");
        insect.setProtectionLevel("未评估");
        insect.setProtectionLevelEn("Not evaluated");
        insect.setHarmLevel(1);
        insect.setDescription("基于 IP102 标签自动生成的基础物种档案，详细百科信息待补充。");
        insect.setDescriptionEn("Auto-generated fallback profile from IP102 labels. Detailed bilingual content is pending.");
        insect.setMorphology("该物种的形态特征信息待补充。");
        insect.setMorphologyEn("Morphology details are pending.");
        insect.setHabits("该物种的生活习性信息待补充。");
        insect.setHabitsEn("Habit details are pending.");
        insect.setRecognitionCount(0);
        insect.setCoverImageUrl("");
        return insect;
    }
}
