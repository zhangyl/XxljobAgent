package org.example;

import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * 拦截 XxlJobServiceImpl 中六个方法的统一拦截器。
 * <p>
 * 逻辑：
 * <ol>
 *   <li><b>remove 方法：</b>在调用原始方法<b>之前</b>，先通过参数 id
 *       调用 {@code XxlJobInfoMapper#loadById(int)} 查询任务详情并暂存，
 *       避免物理删除后无法查询</li>
 *   <li>调用原始方法，拿到返回值 {@code Response<String>}</li>
 *   <li>从参数中提取 {@code LoginInfo#getUserName()}</li>
 *   <li>从返回值或参数中提取任务 id（remove 使用前置提取的 id）</li>
 *   <li>通过 {@code XxlJobInfoMapper#loadById(int)} 查询任务详情（remove 使用前置查询的结果）</li>
 *   <li>将操作人和任务信息 POST 到 {@code http://groot.hz.com/save}</li>
 * </ol>
 * </p>
 */
public class JobServiceInterceptor {

    /**
     * ByteBuddy {@code @RuntimeType} / {@code @SuperCall} 风格的拦截入口。
     * <p>
     * 这里没有使用 {@code @OnMethodExit}，而是主动调用 zuper.call()，
     * 这样可以安全地在调用返回后做后置处理，同时保证原始返回值不受影响。
     * </p>
     */
    @RuntimeType
    public static Object intercept(
            @SuperCall Callable<?> zuper,
            @This Object target,
            @Origin Method method,
            @AllArguments Object[] args
    ) throws Exception {
        String methodName = method.getName();
        System.out.println("[myagent] Intercepted method: " + methodName);

        // ---- remove 方法：前置获取任务信息，避免物理删除后无法查询 ----
        Object preloadedJobInfo = null;
        String preloadedJobIdStr = null;
        if ("remove".equals(methodName)) {
            try {
                preloadedJobIdStr = extractJobId(methodName, args, null);
                if (preloadedJobIdStr != null) {
                    preloadedJobInfo = loadJobInfo(target, Integer.parseInt(preloadedJobIdStr));
                }
            } catch (Exception e) {
                System.err.println("[myagent] Pre-load job info for remove failed: " + e.getMessage());
            }
        }

        // 1. 调用原始方法
        Object result = zuper.call();

        // 2. 后置处理 —— 所有异常捕获，不影响原始业务流程
        try {
            // 提取操作人
            String operator = extractOperator(methodName, args);
            if (operator == null) {
                System.out.println("[myagent] Unable to extract operator, skipped.");
                return result;
            }

            // 提取任务 id 和任务详情（remove 使用前置获取的数据）
            final String jobIdStr;
            final Object jobInfo;

            if ("remove".equals(methodName)) {
                jobIdStr = preloadedJobIdStr;
                jobInfo = preloadedJobInfo;
            } else {
                jobIdStr = extractJobId(methodName, args, result);
                if (jobIdStr == null) {
                    System.out.println("[myagent] Unable to extract jobId, skipped.");
                    return result;
                }
                jobInfo = loadJobInfo(target, Integer.parseInt(jobIdStr));
            }

            if (jobIdStr == null) {
                System.out.println("[myagent] Unable to extract jobId, skipped.");
                return result;
            }

            // 推送到远端接口
            HttpReporter.report(operator, jobInfo, methodName, jobIdStr);

        } catch (Exception e) {
            System.err.println("[myagent] Post-processing failed: " + e.getMessage());
            e.printStackTrace();
        }

        // 3. 返回原始结果
        return result;
    }

    // -------------------------------------------------------------------
    // 辅助方法
    // -------------------------------------------------------------------

    /**
     * 从参数中提取 LoginInfo#getUserName()。
     */
    private static String extractOperator(String methodName, Object[] args) {
        // 找到 LoginInfo 类型的参数
        for (Object arg : args) {
            if (arg != null && arg.getClass().getName().equals("com.xxl.sso.core.model.LoginInfo")) {
                try {
                    Method getUserName = arg.getClass().getMethod("getUserName");
                    return (String) getUserName.invoke(arg);
                } catch (Exception e) {
                    System.err.println("[myagent] Failed to call LoginInfo#getUserName: " + e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * 提取任务 id（字符串形式）。
     * <ul>
     *   <li>{@code add / update} —— 从 {@code Response<String>#getData()} 获取</li>
     *   <li>{@code remove / start / stop} —— 第一个参数 int id</li>
     *   <li>{@code trigger} —— 第二个参数 int jobId</li>
     * </ul>
     */
    private static String extractJobId(String methodName, Object[] args, Object result) {
        try {
            switch (methodName) {
                case "add":
                    // 从返回值 Response<String>#getData() 获取
                    if (result != null) {
                        Method getData = result.getClass().getMethod("getData");
                        return (String) getData.invoke(result);
                    }
                    break;
                case "update":
                    // 第一个参数，调用其 getId() 方法获取任务 id
                    if (args.length >= 1 && args[0] != null) {
                        Method getId = args[0].getClass().getMethod("getId");
                        Object id = getId.invoke(args[0]);
                        if (id instanceof Integer) {
                            return String.valueOf(id);
                        }
                    }
                    break;
                case "remove":
                case "start":
                case "stop":
                    // 第一个参数 int id
                    if (args.length >= 1 && args[0] instanceof Integer) {
                        return String.valueOf(args[0]);
                    }
                    break;

                case "trigger":
                    // 第二个参数 int jobId
                    if (args.length >= 2 && args[1] instanceof Integer) {
                        return String.valueOf(args[1]);
                    }
                    break;

                default:
                    System.err.println("[myagent] Unknown method: " + methodName);
            }
        } catch (Exception e) {
            System.err.println("[myagent] Failed to extract jobId: " + e.getMessage());
        }
        return null;
    }

    /**
     * 通过目标对象上的 XxlJobInfoMapper 字段调用 loadById(int)。
     * <p>
     * 使用按类型查找字段的方式，不依赖具体字段名，兼容性更好。
     * </p>
     */
    private static Object loadJobInfo(Object target, int jobId) {
        try {
            Object mapper = findFieldByType(target, "com.xxl.job.admin.business.mapper.XxlJobInfoMapper");
            if (mapper == null) {
                System.err.println("[myagent] XxlJobInfoMapper field not found on target object.");
                return null;
            }
            Method loadById = mapper.getClass().getMethod("loadById", int.class);
            return loadById.invoke(mapper, jobId);
        } catch (Exception e) {
            System.err.println("[myagent] Failed to load job info: " + e.getMessage());
            return null;
        }
    }

    /**
     * 在对象及其父类中按类型名称查找字段。
     */
    private static Object findFieldByType(Object obj, String typeName) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().getName().equals(typeName)) {
                    field.setAccessible(true);
                    try {
                        return field.get(obj);
                    } catch (IllegalAccessException e) {
                        System.err.println("[myagent] Cannot access field " + field.getName());
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
