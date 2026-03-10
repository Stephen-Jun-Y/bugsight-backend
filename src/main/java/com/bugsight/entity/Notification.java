package com.bugsight.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("notifications")
public class Notification {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String type;
    private Long actorId;
    private String targetType;
    private Long targetId;
    private String content;
    private Integer isRead;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
