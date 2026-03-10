package com.bugsight.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("insect_info")
public class InsectInfo {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String speciesNameCn;
    private String speciesNameEn;
    private String orderName;
    private String familyName;
    private String genusName;
    private String bodyLength;
    private String distribution;
    private String activeSeason;
    private String protectionLevel;
    private Integer harmLevel;
    private String description;
    private String morphology;
    private String habits;
    private Integer recognitionCount;
    private String coverImageUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
