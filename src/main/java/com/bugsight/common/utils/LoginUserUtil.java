package com.bugsight.common.utils;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 当前登录用户工具类，封装 Sa-Token 操作
 */
public class LoginUserUtil {

    public static Long getCurrentUserId() {
        return Long.parseLong(StpUtil.getLoginId().toString());
    }

    public static boolean isLogin() {
        return StpUtil.isLogin();
    }
}
