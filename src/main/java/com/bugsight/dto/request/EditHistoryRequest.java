package com.bugsight.dto.request;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class EditHistoryRequest {
    private String note;
    private String locationName;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
