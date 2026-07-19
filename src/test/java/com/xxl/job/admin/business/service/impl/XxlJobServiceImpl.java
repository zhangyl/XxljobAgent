package com.xxl.job.admin.business.service.impl;

import com.xxl.job.admin.business.mapper.XxlJobInfoMapper;
import com.xxl.job.admin.business.model.XxlJobInfo;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.response.Response;

/**
 * Mock —— 对应真实 XxlJobServiceImpl。
 * <p>
 * 字段 {@code xxlJobInfoMapper} 的类型为 {@code XxlJobInfoMapper}，
 * 拦截器通过类型反射获取该字段。
 * </p>
 */
public class XxlJobServiceImpl {

    // 字段名与真实场景一致，拦截器按类型查找，不依赖名字
    private XxlJobInfoMapper xxlJobInfoMapper = new XxlJobInfoMapper();

    // ========== 需要拦截的六个方法 ==========

    public Response<String> add(XxlJobInfo jobInfo, LoginInfo loginInfo) {
        int newId = 2001;
        return new Response<>(String.valueOf(newId));
    }

    public Response<String> update(XxlJobInfo jobInfo, LoginInfo loginInfo) {
        return new Response<>(String.valueOf(jobInfo.getId()));
    }

    public Response<String> remove(int id, LoginInfo loginInfo) {
        return new Response<>(String.valueOf(id));
    }

    public Response<String> start(int id, LoginInfo loginInfo) {
        return new Response<>(String.valueOf(id));
    }

    public Response<String> stop(int id, LoginInfo loginInfo) {
        return new Response<>(String.valueOf(id));
    }

    public Response<String> trigger(LoginInfo loginInfo, int jobId, String executorParam, String addressList) {
        return new Response<>(String.valueOf(jobId));
    }
}
