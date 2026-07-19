package com.xxl.sso.core.helper;

import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.response.Response;

/**
 * Mock —— 对应真实 XxlSsoHelper。
 */
public class XxlSsoHelper {

    private static volatile LoginInfo mockLoginInfo;

    /**
     * 测试用：设置 mock 的 LoginInfo。
     */
    public static void setMockLoginInfo(LoginInfo loginInfo) {
        mockLoginInfo = loginInfo;
    }

    /**
     * 测试用：清除 mock。
     */
    public static void clearMockLoginInfo() {
        mockLoginInfo = null;
    }

    /**
     * 模拟通过 Cookie 校验登录态并返回 LoginInfo。
     */
    public static Response<LoginInfo> loginCheckWithCookie(Object request, Object response) {
        if (mockLoginInfo != null) {
            return new Response<>(mockLoginInfo);
        }
        // 默认返回一个测试用户
        return new Response<>(new LoginInfo("sso-test-user"));
    }
}
