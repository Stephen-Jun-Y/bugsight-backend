package com.bugsight.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsectCatalogSeedCoverImageTest {

    @Test
    void seedFileDefinesCoverImageUrlForAllSupportedClasses() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path seedFile = Path.of("scripts/data/insect_catalog_seed.json");

        List<Map<String, Object>> records = mapper.readValue(seedFile.toFile(), new TypeReference<>() {
        });

        assertEquals(25, records.size());
        for (Map<String, Object> record : records) {
            Integer classId = ((Number) record.get("class_id")).intValue();
            Object coverImageUrl = record.get("cover_image_url");
            assertEquals("/wiki-covers/" + classId + ".jpg", coverImageUrl, "missing or mismatched cover_image_url for class_id=" + classId);
            assertTrue(((String) coverImageUrl).endsWith(".jpg"));
        }
    }
}
