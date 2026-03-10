package com.bugsight.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("posts")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;
    private String imageUrl;
    private String topicTags;
    private String locationName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;
    private Integer visibility;
    @TableLogic
    private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
