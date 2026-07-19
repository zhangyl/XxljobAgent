package com.xxl.job.admin.business.mapper;

import com.xxl.job.admin.business.model.XxlJobGroup;

/**
 * Mock —— 对应真实 XxlJobGroupMapper。
 */
public class XxlJobGroupMapper {

    /**
     * 模拟按 id 加载执行器组。
     */
    public XxlJobGroup load(int id) {
        XxlJobGroup group = new XxlJobGroup();
        group.setId(id);
        group.setAppname("test-app-" + id);
        group.setTitle("测试执行器组-" + id);
        group.setAddressType(0);
        group.setAddressList("192.168.1." + (id % 256) + ":9999");
        return group;
    }

    /**
     * 模拟按 appname 加载执行器组（insert 后反查）。
     */
    public XxlJobGroup loadByAppname(String appname) {
        XxlJobGroup group = new XxlJobGroup();
        group.setId(3001);  // 模拟 insert 后数据库自增 id
        group.setAppname(appname);
        group.setTitle("新执行器组");
        group.setAddressType(0);
        group.setAddressList("");
        return group;
    }
}
