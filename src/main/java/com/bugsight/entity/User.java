package com.bugsight.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    @TableField(select = false)   // 查询默认不返回密码
    private String password;
    private String avatarUrl;
    private String bio;
    private Integer totalSpecies;
    private Integer totalLocations;
    private Integer totalDays;
    private Integer isActive;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
