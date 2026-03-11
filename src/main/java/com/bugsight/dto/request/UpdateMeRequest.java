package com.bugsight.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMeRequest {

    @Size(min = 2, max = 20, message = "昵称长度为2-20个字符")
    private String nickname;

    @Size(max = 200, message = "简介不超过200字")
    private String bio;

    private String location;

    private String avatarUrl;
}
