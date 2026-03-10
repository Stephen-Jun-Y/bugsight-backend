package com.bugsight.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("favorites")
public class Favorite {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Integer insectId;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
