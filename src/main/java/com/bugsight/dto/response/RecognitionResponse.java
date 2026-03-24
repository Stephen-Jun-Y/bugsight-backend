package com.bugsight.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RecognitionResponse {

    private Long recognitionId;
    private Boolean isUnknown;
    private Species species;
    private BigDecimal confidence;
    private List<SimilarSpecies> similar;
    private String imageUrl;
    private String note;
    private String location;
    private LocalDateTime capturedAt;

    @Data
    @Builder
    public static class Species {
        private Integer id;
        private String name;
        private String latinName;
    }

    @Data
    @Builder
    public static class SimilarSpecies {
        private Integer speciesId;
        private String name;
        private BigDecimal score;
    }
}
