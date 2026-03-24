package com.bugsight.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 放行接口（无需登录） */
    private static final String[] WHITE_LIST = {
            "/auth/login",
            "/auth/register",
            "/doc.html",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/files/**",           // 静态文件访问
            "/insects/popular",    // 首页热门，允许匿名
            "/species/search",     // 物种搜索，允许匿名
            "/species/hot-searches", // 热门搜索，允许匿名
            "/species/*",          // 物种详情，允许匿名
            "/species/*/similar",  // 相似物种，允许匿名
            "/users/*/profile",    // 公开用户主页
            "/users/*/posts",      // 公开用户动态
            "/users/*/favorites",  // 公开用户收藏
            "/users/*/follow-status", // 公开关注状态
            "/health",             // 健康检查
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SaInterceptor authInterceptor = new SaInterceptor(handle ->
                SaRouter.match("/**")
                        .notMatch(WHITE_LIST)
                        .check(StpUtil::checkLogin)
        );

        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (isPreflightRequest(request)) {
                    return true;
                }
                return authInterceptor.preHandle(request, response, handler);
            }
        }).addPathPatterns("/**");
    }

    static boolean isPreflightRequest(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
