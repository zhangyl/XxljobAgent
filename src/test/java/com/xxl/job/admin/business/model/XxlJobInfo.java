package com.xxl.job.admin.business.model;

/**
 * Mock —— 对应真实 XxlJobInfo。
 */
public class XxlJobInfo {

    private int id;
    private String jobName;
    private String jobGroup;
    private String jobDesc;
    private String author;
    private String scheduleType;
    private String scheduleConf;
    private String executorHandler;
    private String executorParam;

    public XxlJobInfo() {}

    public XxlJobInfo(int id, String jobName, String jobGroup, String author) {
        this.id = id;
        this.jobName = jobName;
        this.jobGroup = jobGroup;
        this.author = author;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getJobGroup() { return jobGroup; }
    public void setJobGroup(String jobGroup) { this.jobGroup = jobGroup; }

    public String getJobDesc() { return jobDesc; }
    public void setJobDesc(String jobDesc) { this.jobDesc = jobDesc; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }

    public String getScheduleConf() { return scheduleConf; }
    public void setScheduleConf(String scheduleConf) { this.scheduleConf = scheduleConf; }

    public String getExecutorHandler() { return executorHandler; }
    public void setExecutorHandler(String executorHandler) { this.executorHandler = executorHandler; }

    public String getExecutorParam() { return executorParam; }
    public void setExecutorParam(String executorParam) { this.executorParam = executorParam; }
}
