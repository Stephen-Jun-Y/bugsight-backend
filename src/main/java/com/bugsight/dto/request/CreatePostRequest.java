package com.bugsight.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreatePostRequest {
    @NotBlank(message = "内容不能为空")
    @Size(max = 500, message = "内容不超过500字")
    private String content;
    private String topicTags;
    private String locationName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer visibility = 1;
}
