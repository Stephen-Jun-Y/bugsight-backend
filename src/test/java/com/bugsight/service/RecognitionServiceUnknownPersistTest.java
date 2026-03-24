package com.bugsight.service;

import com.bugsight.entity.RecognitionHistory;
import com.bugsight.mapper.InsectInfoMapper;
import com.bugsight.mapper.RecognitionHistoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecognitionServiceUnknownPersistTest {

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
    @Mock
    private MultipartFile file;

    @InjectMocks
    private RecognitionService recognitionService;

    @Test
    void persistsUnknownRecognitionWithoutIncreasingSpeciesCount() {
        InferenceService.InferenceItem candidate = new InferenceService.InferenceItem();
        candidate.setClassIndex(69);
        candidate.setConfidence(0.41d);

        InferenceService.InferenceResult inferenceResult = new InferenceService.InferenceResult();
        inferenceResult.setIsUnknown(true);
        inferenceResult.setTop1(null);
        inferenceResult.setTop3(List.of(candidate));

        when(inferenceService.predict(file)).thenReturn(inferenceResult);
        when(fileService.saveImage(file)).thenReturn("http://example.com/files/unknown.jpg");
        when(file.getOriginalFilename()).thenReturn("floor.jpg");

        recognitionService.recognize(7L, file, 1, null, null, null);

        ArgumentCaptor<RecognitionHistory> historyCaptor = ArgumentCaptor.forClass(RecognitionHistory.class);
        verify(historyMapper).insert(historyCaptor.capture());
        verify(insectMapper, never()).update(any(), any());

        RecognitionHistory saved = historyCaptor.getValue();
        assertNull(saved.getTop1InsectId());
        assertEquals(new BigDecimal("0.41"), saved.getTop1Confidence());
        assertEquals("[]", saved.getTop3Result());
        assertTrue(saved.getImageUrl().contains("unknown.jpg"));
    }
}
