package com.bugsight.service;

import com.bugsight.dto.response.SpeciesDetailResponse;
import com.bugsight.entity.InsectInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeciesResponseAssemblerTest {

    @Test
    void mapsBilingualFieldsIntoI18nPayload() {
        InsectInfo insect = new InsectInfo();
        insect.setId(0);
        insect.setSpeciesNameCn("稻纵卷叶螟");
        insect.setSpeciesNameEn("Cnaphalocrocis medinalis");
        insect.setOrderName("Lepidoptera");
        insect.setOrderNameCn("鳞翅目");
        insect.setFamilyName("Crambidae");
        insect.setFamilyNameCn("螟蛾科");
        insect.setGenusName("Cnaphalocrocis");
        insect.setGenusNameCn("纵卷叶螟属");
        insect.setBodyLength("成虫体长约 7-10 mm，翅展约 15-18 mm");
        insect.setBodyLengthEn("Adults are about 7-10 mm long with a wingspan of 15-18 mm.");
        insect.setDistribution("广泛分布于东亚、东南亚和南亚稻区。");
        insect.setDistributionEn("Widely distributed across rice-growing regions of East, Southeast, and South Asia.");
        insect.setDescription("中文介绍");
        insect.setDescriptionEn("English description");
        insect.setMorphology("中文形态");
        insect.setMorphologyEn("English morphology");
        insect.setHabits("中文习性");
        insect.setHabitsEn("English habits");
        insect.setActiveSeason("5-10 月");
        insect.setActiveSeasonEn("Usually active from May to October.");
        insect.setProtectionLevel("未评估");
        insect.setProtectionLevelEn("Not evaluated");

        SpeciesDetailResponse response = SpeciesResponseAssembler.toResponse(insect);

        assertEquals("稻纵卷叶螟", response.getSpeciesNameCn());
        assertEquals("Cnaphalocrocis medinalis", response.getSpeciesNameEn());
        assertEquals("鳞翅目", response.getI18n().getOrderName().getCn());
        assertEquals("Lepidoptera", response.getI18n().getOrderName().getEn());
        assertEquals("成虫体长约 7-10 mm，翅展约 15-18 mm", response.getI18n().getBodyLength().getCn());
        assertEquals("Adults are about 7-10 mm long with a wingspan of 15-18 mm.", response.getI18n().getBodyLength().getEn());
        assertEquals("English description", response.getI18n().getDescription().getEn());
    }
}
