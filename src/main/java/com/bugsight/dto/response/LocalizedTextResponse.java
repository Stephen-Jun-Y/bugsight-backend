package com.bugsight.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalizedTextResponse {
    private String cn;
    private String en;
}
