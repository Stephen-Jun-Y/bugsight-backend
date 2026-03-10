package com.bugsight.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recognition_history")
public class RecognitionHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String imageUrl;
    private String imageOriginalName;
    private Integer top1InsectId;
    private BigDecimal top1Confidence;
    private String top3Result;          // JSON string
    private String locationName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String note;
    private Integer uploadSource;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
