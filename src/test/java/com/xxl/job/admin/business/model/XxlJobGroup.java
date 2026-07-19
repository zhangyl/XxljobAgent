package com.xxl.job.admin.business.model;

/**
 * Mock —— 对应真实 XxlJobGroup。
 */
public class XxlJobGroup {

    private int id;
    private String appname;
    private String title;
    private int addressType;   // 0=自动注册, 1=手动录入
    private String addressList;

    public XxlJobGroup() {}

    public XxlJobGroup(int id, String appname, String title) {
        this.id = id;
        this.appname = appname;
        this.title = title;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAppname() { return appname; }
    public void setAppname(String appname) { this.appname = appname; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getAddressType() { return addressType; }
    public void setAddressType(int addressType) { this.addressType = addressType; }

    public String getAddressList() { return addressList; }
    public void setAddressList(String addressList) { this.addressList = addressList; }
}
