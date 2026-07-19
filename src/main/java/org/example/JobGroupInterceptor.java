package org.example;

import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 拦截 JobGroupController 中 insert / update / delete 三个方法的统一拦截器。
 * <p>
 * 逻辑：
 * <ol>
 *   <li><b>delete 方法：</b>在调用原始方法<b>之前</b>，先通过参数中的 ids 列表，
 *       逐个调用 {@code XxlJobGroupMapper#load(int)} 查询执行器组详情并暂存，
 *       避免物理删除后无法查询</li>
 *   <li>调用原始方法</li>
 *   <li>后置处理：
 *     <ul>
 *       <li><b>insert：</b>从参数中取 appname，调用 {@code loadByAppname(appname)} 获取插入后的记录</li>
 *       <li><b>update：</b>从参数中取 id，调用 {@code load(id)} 获取更新后的记录</li>
 *       <li><b>delete：</b>使用前置暂存的记录</li>
 *     </ul>
 *   </li>
 *   <li>通过 {@code RequestContextHolder} → {@code HttpServletRequest} / {@code HttpServletResponse} →
 *       {@code XxlSsoHelper#loginCheckWithCookie} 获取操作人</li>
 *   <li>将操作人和执行器组信息 POST 到 {@code http://groot.hz.com/save}</li>
 * </ol>
 * </p>
 */
public class JobGroupInterceptor {

    /** 测试钩子：设置后跳过 RequestContextHolder 路径，直接返回指定操作人。 */
    private static volatile String testOperator;

    public static void setTestOperator(String operator) {
        testOperator = operator;
    }

    public static void clearTestOperator() {
        testOperator = null;
    }

    @RuntimeType
    public static Object intercept(
            @SuperCall Callable<?> zuper,
            @This Object target,
            @Origin Method method,
            @AllArguments Object[] args
    ) throws Exception {
        String methodName = method.getName();
        System.out.println("[myagent] Intercepted JobGroup method: " + methodName);

        // ---- delete 方法：前置获取执行器组信息，避免物理删除后无法查询 ----
        List<String> preloadedIds = null;
        List<Object> preloadedGroups = null;
        if ("delete".equals(methodName)) {
            try {
                List<Integer> ids = extractDeleteIds(args);
                if (ids != null && !ids.isEmpty()) {
                    preloadedIds = new ArrayList<>();
                    preloadedGroups = new ArrayList<>();
                    for (Integer id : ids) {
                        String idStr = String.valueOf(id);
                        preloadedIds.add(idStr);
                        preloadedGroups.add(loadGroup(target, id));
                        System.out.println("[myagent] Pre-loaded group for delete, id=" + idStr);
                    }
                }
            } catch (Exception e) {
                System.err.println("[myagent] Pre-load group for delete failed: " + e.getMessage());
            }
        }

        // 1. 调用原始方法
        Object result = zuper.call();

        // 2. 后置处理 —— 所有异常捕获，不影响原始业务流程
        try {
            // 提取操作人
            String operator = extractOperator(args, target);
            if (operator == null) {
                System.out.println("[myagent] Unable to extract operator for group operation, skipped.");
                return result;
            }

            switch (methodName) {
                case "insert":
                    handleInsert(target, args, operator);
                    break;
                case "update":
                    handleUpdate(target, args, operator);
                    break;
                case "delete":
                    handleDelete(operator, preloadedIds, preloadedGroups);
                    break;
                default:
                    System.err.println("[myagent] Unknown group method: " + methodName);
            }

        } catch (Exception e) {
            System.err.println("[myagent] Group post-processing failed: " + e.getMessage());
            e.printStackTrace();
        }

        // 3. 返回原始结果
        return result;
    }

    // -------------------------------------------------------------------
    // 后置处理
    // -------------------------------------------------------------------

    /**
     * insert 后置处理：通过 appname 反查插入后的记录。
     */
    private static void handleInsert(Object target, Object[] args, String operator) {
        if (args.length < 1 || args[0] == null) {
            System.out.println("[myagent] insert: missing XxlJobGroup argument, skipped.");
            return;
        }
        String appname = getAppname(args[0]);
        String groupId = getGroupId(args[0]);
        if (appname == null) {
            System.out.println("[myagent] insert: unable to extract appname, skipped.");
            return;
        }
        Object group = loadGroupByAppname(target, appname);
        HttpReporter.report(operator, group, "insertGroup", groupId);
    }

    /**
     * update 后置处理：通过 id 查询更新后的记录。
     */
    private static void handleUpdate(Object target, Object[] args, String operator) {
        if (args.length < 1 || args[0] == null) {
            System.out.println("[myagent] update: missing XxlJobGroup argument, skipped.");
            return;
        }
        String groupId = getGroupId(args[0]);
        if (groupId == null) {
            System.out.println("[myagent] update: unable to extract id, skipped.");
            return;
        }
        Object group = loadGroup(target, Integer.parseInt(groupId));
        HttpReporter.report(operator, group, "updateGroup", groupId);
    }

    /**
     * delete 后置处理：使用前置获取的记录逐个上报。
     */
    private static void handleDelete(String operator, List<String> preloadedIds, List<Object> preloadedGroups) {
        if (preloadedIds == null || preloadedGroups == null) {
            System.out.println("[myagent] delete: no preloaded data, skipped.");
            return;
        }
        for (int i = 0; i < preloadedIds.size(); i++) {
            HttpReporter.report(operator, preloadedGroups.get(i), "deleteGroup", preloadedIds.get(i));
        }
    }

    // -------------------------------------------------------------------
    // 操作人提取
    // -------------------------------------------------------------------

    /**
     * 提取操作人。
     * <ol>
     *   <li>测试钩子（方便单测）</li>
     *   <li>Spring RequestContextHolder → HttpServletRequest/HttpServletResponse → XxlSsoHelper.loginCheckWithCookie</li>
     *   <li>参数中的 LoginInfo（兼容直接传参的场景）</li>
     * </ol>
     */
    private static String extractOperator(Object[] args, Object target) {
        // 测试钩子
        if (testOperator != null) {
            return testOperator;
        }

        // 路径1: Spring RequestContextHolder → HttpServletRequest → XxlSsoHelper
        String operator = extractOperatorViaSpring();
        if (operator != null) {
            return operator;
        }

        // 路径2: 从参数中查找 LoginInfo
        return extractOperatorFromArgs(args);
    }

    /**
     * 通过 Spring RequestContextHolder 获取当前请求，再调用 XxlSsoHelper 获取登录信息。
     * 全程反射调用，无编译期依赖。
     */
    private static String extractOperatorViaSpring() {
        try {
            // RequestContextHolder.currentRequestAttributes()
            Class<?> holderClass = Class.forName("org.springframework.web.context.request.RequestContextHolder");
            Method getRequestAttributes = holderClass.getMethod("currentRequestAttributes");
            Object attributes = getRequestAttributes.invoke(null);

            // ServletRequestAttributes.getRequest()
            Method getRequest = attributes.getClass().getMethod("getRequest");
            Object request = getRequest.invoke(attributes);

            // ServletRequestAttributes.getResponse()
            Method getResponse = attributes.getClass().getMethod("getResponse");
            Object response = getResponse.invoke(attributes);

            // XxlSsoHelper.loginCheckWithCookie(HttpServletRequest, HttpServletResponse)
            Class<?> ssoHelperClass = Class.forName("com.xxl.sso.core.helper.XxlSsoHelper");
            // 找到 HttpServletRequest 接口类型
            Class<?> requestInterface = request.getClass().getInterfaces().length > 0
                    ? request.getClass().getInterfaces()[0]
                    : request.getClass();
            // 找到 HttpServletResponse 接口类型
            Class<?> responseInterface = response != null && response.getClass().getInterfaces().length > 0
                    ? response.getClass().getInterfaces()[0]
                    : (response != null ? response.getClass() : null);
            Method loginCheckMethod = ssoHelperClass.getMethod("loginCheckWithCookie",
                    requestInterface, responseInterface);
            Object loginInfoResp = loginCheckMethod.invoke(null, request, response);

            // Response<LoginInfo>.getData()
            Method getData = loginInfoResp.getClass().getMethod("getData");
            Object loginInfo = getData.invoke(loginInfoResp);
            if (loginInfo != null) {
                Method getUserName = loginInfo.getClass().getMethod("getUserName");
                return (String) getUserName.invoke(loginInfo);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[myagent] Spring not on classpath, skip RequestContextHolder path.");
        } catch (Exception e) {
            System.err.println("[myagent] Failed to extract operator via Spring: " + e.getMessage());
        }
        return null;
    }

    /**
     * 从方法参数中查找 LoginInfo。
     */
    private static String extractOperatorFromArgs(Object[] args) {
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

    // -------------------------------------------------------------------
    // Mapper 访问
    // -------------------------------------------------------------------

    /**
     * 通过 XxlJobGroupMapper#load(int) 查询执行器组。
     */
    private static Object loadGroup(Object target, int id) {
        try {
            Object mapper = findFieldByType(target, "com.xxl.job.admin.business.mapper.XxlJobGroupMapper");
            if (mapper == null) {
                System.err.println("[myagent] XxlJobGroupMapper field not found on target object.");
                return null;
            }
            Method load = mapper.getClass().getMethod("load", int.class);
            return load.invoke(mapper, id);
        } catch (Exception e) {
            System.err.println("[myagent] Failed to load group by id=" + id + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过 XxlJobGroupMapper#loadByAppname(String) 查询执行器组（insert 后反查）。
     */
    private static Object loadGroupByAppname(Object target, String appname) {
        try {
            Object mapper = findFieldByType(target, "com.xxl.job.admin.business.mapper.XxlJobGroupMapper");
            if (mapper == null) {
                System.err.println("[myagent] XxlJobGroupMapper field not found on target object.");
                return null;
            }
            Method loadByAppname = mapper.getClass().getMethod("loadByAppname", String.class);
            return loadByAppname.invoke(mapper, appname);
        } catch (Exception e) {
            System.err.println("[myagent] Failed to load group by appname=" + appname + ": " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------
    // 参数提取
    // -------------------------------------------------------------------

    /**
     * 从 delete 方法的参数中提取 ids 列表。
     * 兼容 {@code List<Integer>} 和 {@code Integer[]}。
     */
    @SuppressWarnings("unchecked")
    private static List<Integer> extractDeleteIds(Object[] args) {
        if (args.length < 1 || args[0] == null) {
            return null;
        }
        if (args[0] instanceof List) {
            return (List<Integer>) args[0];
        }
        if (args[0] instanceof Integer[]) {
            List<Integer> list = new ArrayList<>();
            for (Integer id : (Integer[]) args[0]) {
                list.add(id);
            }
            return list;
        }
        // 兼容 int[]
        if (args[0] instanceof int[]) {
            List<Integer> list = new ArrayList<>();
            for (int id : (int[]) args[0]) {
                list.add(id);
            }
            return list;
        }
        System.err.println("[myagent] delete: unexpected argument type: " + args[0].getClass().getName());
        return null;
    }

    /**
     * 调用 XxlJobGroup#getId()。
     */
    private static String getGroupId(Object xxlJobGroup) {
        try {
            Method getId = xxlJobGroup.getClass().getMethod("getId");
            Object id = getId.invoke(xxlJobGroup);
            if (id instanceof Integer) {
                return String.valueOf(id);
            }
        } catch (Exception e) {
            System.err.println("[myagent] Failed to get group id: " + e.getMessage());
        }
        return null;
    }

    /**
     * 调用 XxlJobGroup#getAppname()。
     */
    private static String getAppname(Object xxlJobGroup) {
        try {
            Method getAppname = xxlJobGroup.getClass().getMethod("getAppname");
            return (String) getAppname.invoke(xxlJobGroup);
        } catch (Exception e) {
            System.err.println("[myagent] Failed to get group appname: " + e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------
    // 反射工具
    // -------------------------------------------------------------------

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
