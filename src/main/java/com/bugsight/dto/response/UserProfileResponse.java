package com.bugsight.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileResponse {

    private Long id;
    private String nickname;
    private String bio;
    private String avatarUrl;
    private String location;
}
