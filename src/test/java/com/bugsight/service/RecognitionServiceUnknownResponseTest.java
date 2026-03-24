package com.bugsight.service;

import com.bugsight.dto.response.RecognitionResponse;
import com.bugsight.entity.RecognitionHistory;
import com.bugsight.mapper.InsectInfoMapper;
import com.bugsight.mapper.RecognitionHistoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RecognitionServiceUnknownResponseTest {

    @Mock
    private RecognitionHistoryMapper historyMapper;
    @Mock
    private InsectInfoMapper insectMapper;
    @Mock
    private InsectCatalogService insectCatalogService;
    @Mock
    private InferenceService inferenceService;
    @Mock
    private FileService fileService;

    @InjectMocks
    private RecognitionService recognitionService;

    @Test
    void returnsUnknownResponseWhenTop1SpeciesIsMissing() {
        RecognitionHistory history = new RecognitionHistory();
        history.setId(9L);
        history.setTop1InsectId(null);
        history.setTop1Confidence(new BigDecimal("0.4200"));
        history.setTop3Result("[]");
        history.setImageUrl("http://example.com/files/unknown.jpg");

        RecognitionResponse response = recognitionService.toRecognitionResponse(history);

        assertTrue(response.getIsUnknown());
        assertNull(response.getSpecies());
        assertEquals(0, response.getSimilar().size());
        assertEquals(new BigDecimal("0.4200"), response.getConfidence());
    }
}
