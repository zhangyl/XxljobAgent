package org.example;

import com.xxl.job.admin.business.controller.JobGroupController;
import com.xxl.job.admin.business.model.XxlJobGroup;
import com.xxl.job.admin.business.model.XxlJobInfo;
import com.xxl.job.admin.business.service.impl.XxlJobServiceImpl;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.response.Response;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.Assert.*;

/**
 * Mock 测试 —— 验证 Java Agent 拦截逻辑端到端正确。
 * <p>
 * 通过 {@link HttpReporter#createCollectingHook()} 捕获上报的 JSON payload。
 * 使用 {@link ClassLoadingStrategy.Default#WRAPPER} 确保每次测试独立加载。
 * </p>
 */
public class AgentTest {

    /** 测试钩子收集的 payload */
    private List<String> captured;

    @Before
    public void setUp() {
        captured = HttpReporter.createCollectingHook();
    }

    @After
    public void tearDown() {
        HttpReporter.setTestHook(null);
        JobGroupInterceptor.clearTestOperator();
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /**
     * 创建一个已织入拦截器的 XxlJobServiceImpl 实例。
     * 使用 WRAPPER 策略，每次调用在独立 ClassLoader 中加载，避免 "already loaded" 错误。
     */
    private static XxlJobServiceImpl createInstrumentedService() throws Exception {
        Class<? extends XxlJobServiceImpl> instrumentedClass = new ByteBuddy()
                .subclass(XxlJobServiceImpl.class)
                .method(named("add")
                        .or(named("update"))
                        .or(named("remove"))
                        .or(named("start"))
                        .or(named("stop"))
                        .or(named("trigger")))
                .intercept(MethodDelegation.to(JobServiceInterceptor.class))
                .make()
                .load(XxlJobServiceImpl.class.getClassLoader(),
                        ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();

        return instrumentedClass.newInstance();
    }

    /**
     * 创建仅织入指定方法的 XxlJobServiceImpl 实例。
     */
    private static XxlJobServiceImpl createInstrumentedServiceFor(String... methodNames) throws Exception {
        net.bytebuddy.matcher.ElementMatcher.Junction<
                net.bytebuddy.description.method.MethodDescription> matcher = named(methodNames[0]);
        for (int i = 1; i < methodNames.length; i++) {
            matcher = matcher.or(named(methodNames[i]));
        }

        Class<? extends XxlJobServiceImpl> instrumentedClass = new ByteBuddy()
                .subclass(XxlJobServiceImpl.class)
                .method(matcher)
                .intercept(MethodDelegation.to(JobServiceInterceptor.class))
                .make()
                .load(XxlJobServiceImpl.class.getClassLoader(),
                        ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();

        return instrumentedClass.newInstance();
    }

    // ================================================================
    // 测试用例
    // ================================================================

    /**
     * 核心测试：验证六个方法的拦截 + ID 提取 + 操作人提取 + 上报。
     */
    @Test
    public void testAllSixMethodsInterception() throws Exception {
        XxlJobServiceImpl service = createInstrumentedService();
        LoginInfo loginInfo = new LoginInfo("zhangsan");

        // ---- 依次调用六个方法 ----

        Response<String> addResp = service.add(
                new XxlJobInfo(0, "new-job", "test", "admin"), loginInfo);
        assertNotNull(addResp);
        assertEquals("2001", addResp.getData());

        Response<String> updateResp = service.update(
                new XxlJobInfo(3001, "update-job", "test", "admin"), loginInfo);
        assertNotNull(updateResp);
        assertEquals("3001", updateResp.getData());

        Response<String> removeResp = service.remove(4001, loginInfo);
        assertNotNull(removeResp);
        assertEquals("4001", removeResp.getData());

        Response<String> startResp = service.start(5001, loginInfo);
        assertNotNull(startResp);
        assertEquals("5001", startResp.getData());

        Response<String> stopResp = service.stop(6001, loginInfo);
        assertNotNull(stopResp);
        assertEquals("6001", stopResp.getData());

        Response<String> triggerResp = service.trigger(loginInfo, 7001, "param=test", "192.168.1.1:9999");
        assertNotNull(triggerResp);
        assertEquals("7001", triggerResp.getData());

        // ---- 验证上报数据 ----
        System.out.println("[test] Total captured payloads: " + captured.size());
        for (int i = 0; i < captured.size(); i++) {
            System.out.println("[test]   [" + i + "] " + captured.get(i));
        }
        assertEquals("Should have 6 reports", 6, captured.size());

        // 验证每条上报都包含必要字段
        for (String payload : captured) {
            assertTrue("Payload should contain operator", payload.contains("\"operator\":\"zhangsan\""));
            assertTrue("Payload should contain jobInfo", payload.contains("\"jobInfo\""));
            assertTrue("Payload should contain action", payload.contains("\"action\""));
            assertTrue("Payload should contain jobId", payload.contains("\"jobId\""));
        }

        // 验证 action 字段覆盖全部六种操作
        assertTrue("should contain add",    captured.stream().anyMatch(p -> p.contains("\"action\":\"add\"")));
        assertTrue("should contain update", captured.stream().anyMatch(p -> p.contains("\"action\":\"update\"")));
        assertTrue("should contain remove", captured.stream().anyMatch(p -> p.contains("\"action\":\"remove\"")));
        assertTrue("should contain start",  captured.stream().anyMatch(p -> p.contains("\"action\":\"start\"")));
        assertTrue("should contain stop",   captured.stream().anyMatch(p -> p.contains("\"action\":\"stop\"")));
        assertTrue("should contain trigger",captured.stream().anyMatch(p -> p.contains("\"action\":\"trigger\"")));

        // 验证 jobId 正确
        assertTrue("jobId 2001", captured.stream().anyMatch(p -> p.contains("\"jobId\":\"2001\"")));
        assertTrue("jobId 3001", captured.stream().anyMatch(p -> p.contains("\"jobId\":\"3001\"")));
        assertTrue("jobId 4001", captured.stream().anyMatch(p -> p.contains("\"jobId\":\"4001\"")));
        assertTrue("jobId 5001", captured.stream().anyMatch(p -> p.contains("\"jobId\":\"5001\"")));
        assertTrue("jobId 6001", captured.stream().anyMatch(p -> p.contains("\"jobId\":\"6001\"")));
        assertTrue("jobId 7001", captured.stream().anyMatch(p -> p.contains("\"jobId\":\"7001\"")));

        // 验证 jobInfo 包含真实数据（由 mock mapper 返回）
        assertTrue("jobInfo should contain test-job-2001",
                captured.stream().anyMatch(p -> p.contains("test-job-2001")));
        assertTrue("jobInfo should contain test-job-4001",
                captured.stream().anyMatch(p -> p.contains("test-job-4001")));

        System.out.println("[test] ✅ All 6 methods intercepted and verified!");
    }

    /**
     * 测试 LoginInfo 为 null 的边界情况：拦截器应跳过上报且不抛异常。
     */
    @Test
    public void testNullLoginInfoShouldNotBreak() throws Exception {
        XxlJobServiceImpl service = createInstrumentedServiceFor("add");

        // LoginInfo 为 null → extractOperator 返回 null → 跳过上报，方法正常返回
        Response<String> resp = service.add(new XxlJobInfo(), null);
        assertNotNull(resp);
        assertTrue("No payload should be captured when loginInfo is null", captured.isEmpty());

        System.out.println("[test] ✅ null LoginInfo test passed (no exception, no report)");
    }

    /**
     * 测试 remove 时 id 为 0 的边界情况。
     */
    @Test
    public void testRemoveWithZeroId() throws Exception {
        XxlJobServiceImpl service = createInstrumentedServiceFor("remove");

        Response<String> resp = service.remove(0, new LoginInfo("lisi"));
        assertNotNull(resp);
        assertEquals("0", resp.getData());

        assertEquals("Should have 1 report", 1, captured.size());
        String payload = captured.get(0);
        assertTrue("should contain operator lisi", payload.contains("\"operator\":\"lisi\""));
        assertTrue("should contain jobId 0", payload.contains("\"jobId\":\"0\""));
        assertTrue("should contain action remove", payload.contains("\"action\":\"remove\""));
        assertTrue("should contain jobInfo for id 0", payload.contains("test-job-0"));

        System.out.println("[test] ✅ remove(0) test passed");
    }

    /**
     * 测试 trigger 方法中 LoginInfo 在第一个参数的特殊顺序。
     */
    @Test
    public void testTriggerMethodParameterOrder() throws Exception {
        XxlJobServiceImpl service = createInstrumentedServiceFor("trigger");
        LoginInfo loginInfo = new LoginInfo("triggerUser");

        Response<String> resp = service.trigger(loginInfo, 8888, "p=1", "10.0.0.1:8080");
        assertNotNull(resp);
        assertEquals("8888", resp.getData());

        assertEquals("Should have 1 report", 1, captured.size());
        String payload = captured.get(0);
        assertTrue(payload.contains("\"operator\":\"triggerUser\""));
        assertTrue(payload.contains("\"jobId\":\"8888\""));
        assertTrue(payload.contains("\"action\":\"trigger\""));

        System.out.println("[test] ✅ trigger parameter order test passed");
    }

    // ================================================================
    // JobGroupController 测试
    // ================================================================

    /**
     * 创建一个已织入拦截器的 JobGroupController 实例。
     */
    private static JobGroupController createInstrumentedGroupController() throws Exception {
        Class<? extends JobGroupController> instrumentedClass = new ByteBuddy()
                .subclass(JobGroupController.class)
                .method(named("insert")
                        .or(named("update"))
                        .or(named("delete")))
                .intercept(MethodDelegation.to(JobGroupInterceptor.class))
                .make()
                .load(JobGroupController.class.getClassLoader(),
                        ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();

        return instrumentedClass.newInstance();
    }

    /**
     * 核心测试：验证 JobGroupController 三个方法的拦截 + 上报。
     */
    @Test
    public void testJobGroupInsertUpdateDelete() throws Exception {
        JobGroupController controller = createInstrumentedGroupController();
        JobGroupInterceptor.setTestOperator("groupAdmin");

        // ---- insert ----
        XxlJobGroup newGroup = new XxlJobGroup(0, "test-app-insert", "新执行器");
        Response<String> insertResp = controller.insert(newGroup);
        assertNotNull(insertResp);
        assertEquals("3001", insertResp.getData());

        // ---- update ----
        XxlJobGroup updateGroup = new XxlJobGroup(4001, "test-app-update", "更新执行器");
        Response<String> updateResp = controller.update(updateGroup);
        assertNotNull(updateResp);
        assertEquals("4001", updateResp.getData());

        // ---- delete ----
        Response<String> deleteResp = controller.delete(Arrays.asList(5001, 5002));
        assertNotNull(deleteResp);

        // ---- 验证上报数据 ----
        System.out.println("[test] Total captured group payloads: " + captured.size());
        for (int i = 0; i < captured.size(); i++) {
            System.out.println("[test]   [" + i + "] " + captured.get(i));
        }
        assertEquals("Should have 4 reports (1 insert + 1 update + 2 delete)",
                4, captured.size());

        // 验证 insert 上报
        String insertPayload = captured.get(0);
        assertTrue("insert should contain operator", insertPayload.contains("\"operator\":\"groupAdmin\""));
        assertTrue("insert should contain action insertGroup", insertPayload.contains("\"action\":\"insertGroup\""));
        assertTrue("insert should contain jobInfo", insertPayload.contains("\"jobInfo\""));
        assertTrue("insert should have jobInfo with appname",
                insertPayload.contains("test-app-insert"));

        // 验证 update 上报
        String updatePayload = captured.get(1);
        assertTrue("update should contain action updateGroup", updatePayload.contains("\"action\":\"updateGroup\""));
        assertTrue("update should contain jobId 4001", updatePayload.contains("\"jobId\":\"4001\""));
        assertTrue("update should have jobInfo with id 4001",
                updatePayload.contains("test-app-4001"));

        // 验证 delete 上报（5001）
        String deletePayload1 = captured.get(2);
        assertTrue("delete should contain action deleteGroup", deletePayload1.contains("\"action\":\"deleteGroup\""));
        assertTrue("delete should contain jobId 5001", deletePayload1.contains("\"jobId\":\"5001\""));
        assertTrue("delete should have jobInfo with id 5001",
                deletePayload1.contains("test-app-5001"));

        // 验证 delete 上报（5002）
        String deletePayload2 = captured.get(3);
        assertTrue("delete should contain jobId 5002", deletePayload2.contains("\"jobId\":\"5002\""));
        assertTrue("delete should have jobInfo with id 5002",
                deletePayload2.contains("test-app-5002"));

        System.out.println("[test] ✅ JobGroup insert/update/delete all verified!");
    }

    /**
     * 测试没有操作人时的优雅降级：不抛异常，不上报，原始方法正常返回。
     */
    @Test
    public void testJobGroupWithoutOperator() throws Exception {
        JobGroupController controller = createInstrumentedGroupController();
        // 不设置 testOperator，且环境中无 Spring → extractOperator 返回 null

        XxlJobGroup group = new XxlJobGroup(0, "noop-group", "无操作人");
        Response<String> resp = controller.insert(group);
        assertNotNull(resp);
        assertEquals("3001", resp.getData());

        assertTrue("No payload should be captured when operator is null", captured.isEmpty());

        System.out.println("[test] ✅ JobGroup without operator test passed (no exception, no report)");
    }

    /**
     * 测试 delete 没有参数时的边界情况。
     */
    @Test
    public void testJobGroupDeleteWithEmptyArgs() throws Exception {
        JobGroupController controller = createInstrumentedGroupController();
        JobGroupInterceptor.setTestOperator("groupAdmin");

        // delete 传 null —— 模拟异常场景，验证不崩溃
        Response<String> resp = controller.delete(null);
        assertNotNull(resp);

        // preloadedIds 为 null → handleDelete 跳过上报
        assertTrue("No payload when delete args are invalid", captured.isEmpty());

        System.out.println("[test] ✅ JobGroup delete with null args test passed");
    }
}
