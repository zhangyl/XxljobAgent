package org.example;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Java Agent 入口类。
 * <p>
 * 拦截两类目标：
 * <ol>
 *   <li>{@code XxlJobServiceImpl} 中 add / update / remove / start / stop / trigger 六个方法</li>
 *   <li>{@code JobGroupController} 中 insert / update / delete 三个方法</li>
 * </ol>
 * 在方法调用成功后，将操作人和变更信息推送到 http://groot.hz.com/save 接口。
 * </p>
 *
 * <pre>
 *   java -javaagent:myagent.jar -jar xxl-job-admin.jar
 * </pre>
 */
public class AgentEntry {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[myagent] Agent loaded, starting instrumentation...");

        // ---- 拦截 XxlJobServiceImpl ----
        new AgentBuilder.Default()
                .type(named("com.xxl.job.admin.business.service.impl.XxlJobServiceImpl"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.method(buildJobServiceMethodMatcher())
                                .intercept(net.bytebuddy.implementation.MethodDelegation
                                        .to(JobServiceInterceptor.class))
                )
                .installOn(inst);

        // ---- 拦截 JobGroupController ----
        new AgentBuilder.Default()
                .type(named("com.xxl.job.admin.business.controller.JobGroupController"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.method(buildJobGroupMethodMatcher())
                                .intercept(net.bytebuddy.implementation.MethodDelegation
                                        .to(JobGroupInterceptor.class))
                )
                .installOn(inst);

        System.out.println("[myagent] Instrumentation installed successfully.");
    }

    /**
     * 支持运行时 attach 的场景
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }

    /**
     * 构造匹配 XxlJobServiceImpl 六个目标方法的 matcher。
     */
    private static ElementMatcher<MethodDescription> buildJobServiceMethodMatcher() {
        return named("add")
                .or(named("update"))
                .or(named("remove"))
                .or(named("start"))
                .or(named("stop"))
                .or(named("trigger"));
    }

    /**
     * 构造匹配 JobGroupController 三个目标方法的 matcher。
     */
    private static ElementMatcher<MethodDescription> buildJobGroupMethodMatcher() {
        return named("insert")
                .or(named("update"))
                .or(named("delete"));
    }
}
