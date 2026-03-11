package com.bugsight.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AuthTokenResponse {

    private String accessToken;
    private String refreshToken;

    /** 兼容旧前端 */
    private String token;

    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Map<String, Object> user;
}
