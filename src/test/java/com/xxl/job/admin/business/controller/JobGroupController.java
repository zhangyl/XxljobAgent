package com.xxl.job.admin.business.controller;

import com.xxl.job.admin.business.mapper.XxlJobGroupMapper;
import com.xxl.job.admin.business.model.XxlJobGroup;
import com.xxl.tool.response.Response;

import java.util.List;

/**
 * Mock —— 对应真实 JobGroupController。
 * <p>
 * 字段 {@code xxlJobGroupMapper} 的类型为 {@code XxlJobGroupMapper}，
 * 拦截器通过类型反射获取该字段。
 * </p>
 */
public class JobGroupController {

    private XxlJobGroupMapper xxlJobGroupMapper = new XxlJobGroupMapper();

    /**
     * 新增执行器组。
     */
    public Response<String> insert(XxlJobGroup xxlJobGroup) {
        // 模拟 insert，返回数据库自增 id
        return new Response<>(String.valueOf(3001));
    }

    /**
     * 更新执行器组。
     */
    public Response<String> update(XxlJobGroup xxlJobGroup) {
        return new Response<>(String.valueOf(xxlJobGroup.getId()));
    }

    /**
     * 删除执行器组（支持批量）。
     * <p>
     * 注意：真实方法参数名是 {@code ids[]}，这里简化为 List。
     * </p>
     */
    public Response<String> delete(List<Integer> ids) {
        return new Response<>("ok");
    }
}
