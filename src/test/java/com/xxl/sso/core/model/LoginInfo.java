package com.xxl.sso.core.model;

/**
 * Mock —— 对应真实 LoginInfo。
 */
public class LoginInfo {

    private String userName;

    public LoginInfo() {}

    public LoginInfo(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
