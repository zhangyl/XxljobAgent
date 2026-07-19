package com.xxl.job.admin.business.mapper;

import com.xxl.job.admin.business.model.XxlJobInfo;

/**
 * Mock —— 对应真实 XxlJobInfoMapper。
 */
public class XxlJobInfoMapper {

    /**
     * 模拟按 id 加载任务。
     */
    public XxlJobInfo loadById(int id) {
        // 返回一个 mock 对象，id 由调用方传入
        XxlJobInfo info = new XxlJobInfo();
        info.setId(id);
        info.setJobName("test-job-" + id);
        info.setJobGroup("test-group");
        info.setAuthor("admin");
        info.setJobDesc("mock job description");
        info.setScheduleType("CRON");
        info.setScheduleConf("0 0/1 * * * ?");
        info.setExecutorHandler("demoHandler");
        info.setExecutorParam("param=value");
        return info;
    }
}
