package org.example;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Java Agent 入口类。
 * <p>
 * 拦截 {@code com.xxl.job.admin.business.service.impl.XxlJobServiceImpl} 中
 * add / update / remove / start / stop / trigger 六个方法，在方法调用成功后，
 * 将操作人和任务信息推送到 http://groot.hz.com/save 接口。
 * </p>
 *
 * <pre>
 *   java -javaagent:myagent.jar -jar xxl-job-admin.jar
 * </pre>
 */
public class AgentEntry {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[myagent] Agent loaded, starting instrumentation...");

        new AgentBuilder.Default()
                .type(named("com.xxl.job.admin.business.service.impl.XxlJobServiceImpl"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.method(buildMethodMatcher())
                                .intercept(net.bytebuddy.implementation.MethodDelegation
                                        .to(JobServiceInterceptor.class))
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
     * 构造匹配六个目标方法的 matcher。
     */
    private static ElementMatcher<MethodDescription> buildMethodMatcher() {
        return named("add")
                .or(named("update"))
                .or(named("remove"))
                .or(named("start"))
                .or(named("stop"))
                .or(named("trigger"));
    }
}
