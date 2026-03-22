package com.bugsight.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bugsight.common.exception.BusinessException;
import com.bugsight.common.result.ResultCode;
import com.bugsight.dto.request.ChangePasswordRequest;
import com.bugsight.dto.request.EditProfileRequest;
import com.bugsight.dto.request.LoginRequest;
import com.bugsight.dto.request.RegisterRequest;
import com.bugsight.dto.request.UpdateMeRequest;
import com.bugsight.dto.response.UserProfileResponse;
import com.bugsight.entity.User;
import com.bugsight.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;

    @Transactional
    public User register(RegisterRequest req) {
        // 检查邮箱唯一性
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, req.getEmail())) > 0) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }
        // 检查用户名唯一性
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername())) > 0) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(BCrypt.hashpw(req.getPassword()));
        user.setTotalSpecies(0);
        user.setTotalLocations(0);
        user.setTotalDays(0);
        user.setIsActive(1);
        userMapper.insert(user);
        return user;
    }

    public User login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .select(
                        User::getId,
                        User::getUsername,
                        User::getEmail,
                        User::getPassword,
                        User::getAvatarUrl,
                        User::getIsActive
                )
                .eq(User::getEmail, req.getEmail()));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getIsActive() == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        if (user.getPassword() == null || !BCrypt.checkpw(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.WRONG_PASSWORD);
        }
        StpUtil.login(user.getId());
        return user;
    }

    public String getToken() {
        SaTokenInfo info = StpUtil.getTokenInfo();
        return info.getTokenValue();
    }

    public User getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Transactional
    public User editProfile(Long userId, EditProfileRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);

        if (req.getUsername() != null && !req.getUsername().equals(user.getUsername())) {
            if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, req.getUsername())) > 0) {
                throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
            }
            user.setUsername(req.getUsername());
        }
        if (req.getBio() != null) user.setBio(req.getBio());
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
        userMapper.updateById(user);
        return user;
    }


    public UserProfileResponse getCurrentUserProfile(Long userId) {
        User user = getProfile(userId);
        return toUserProfile(user);
    }

    @Transactional
    public UserProfileResponse updateCurrentUserProfile(Long userId, UpdateMeRequest req) {
        EditProfileRequest editReq = new EditProfileRequest();
        editReq.setUsername(req.getNickname());
        editReq.setBio(req.getBio());
        editReq.setAvatarUrl(req.getAvatarUrl());
        User updated = editProfile(userId, editReq);
        return toUserProfile(updated, req.getLocation());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (!BCrypt.checkpw(req.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.WRONG_PASSWORD);
        }
        user.setPassword(BCrypt.hashpw(req.getNewPassword()));
        userMapper.updateById(user);
    }

    @Transactional
    public void deleteCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        user.setIsActive(0);
        userMapper.updateById(user);
        StpUtil.logout(userId);
    }

    private UserProfileResponse toUserProfile(User user) {
        return toUserProfile(user, "");
    }

    private UserProfileResponse toUserProfile(User user, String location) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .nickname(user.getUsername())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .location(location != null ? location : "")
                .build();
    }

}