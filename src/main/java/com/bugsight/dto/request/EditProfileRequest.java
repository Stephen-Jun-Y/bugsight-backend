package com.bugsight.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EditProfileRequest {
    @Size(min = 2, max = 20, message = "用户名长度为2-20个字符")
    private String username;
    @Size(max = 200, message = "简介不超过200字")
    private String bio;
    private String avatarUrl;
}
