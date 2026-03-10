package com.bugsight.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("post_likes")
public class PostLike {
    @TableId(type = IdType.AUTO) private Long id;
    private Long postId;
    private Long userId;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
