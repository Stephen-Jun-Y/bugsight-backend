package com.bugsight.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicUserProfileResponse {

    private Long id;
    private String nickname;
    private String bio;
    private String avatarUrl;
    private String location;
    private Boolean isFollowing;
    private Boolean isSelf;
    private Long recognitionCount;
    private Long postCount;
    private Long receivedLikeCount;
    private Long favoriteCount;
    private Long followerCount;
    private Long followingCount;
}
