package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 上报工具 —— 将操作人和任务信息 POST 到 http://groot.hz.com/save。
 * <p>
 * 测试时可通过 {@link #setTestHook(Consumer)} 注入自定义处理器，
 * 避免实际发起 HTTP 请求。
 * </p>
 */
public class HttpReporter {

    private static final String REPORT_URL = System.getProperty(
            "myagent.report.url", "http://groot.hz.com/save");

    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();

    /**
     * 测试钩子：如果非 null，report() 会将 JSON payload 交给此接口处理，
     * 而不发起真实 HTTP 请求。
     */
    public interface Consumer {
        void accept(String json);
    }

    private static volatile Consumer testHook;

    /**
     * 注入测试钩子。测试结束时调用 {@code setTestHook(null)} 恢复。
     */
    public static void setTestHook(Consumer hook) {
        testHook = hook;
    }

    /**
     * 获取测试钩子捕获的 payload 列表（便捷方法）。
     */
    public static List<String> createCollectingHook() {
        List<String> list = Collections.synchronizedList(new ArrayList<String>());
        setTestHook(new Consumer() {
            @Override
            public void accept(String json) {
                list.add(json);
                System.out.println("[myagent/test-hook] Captured: " + json);
            }
        });
        return list;
    }

    /**
     * 上报操作人和任务信息。
     *
     * @param operator   操作人（userName）
     * @param jobInfo    任务详情对象（XxlJobInfo），可能为 null
     * @param action     操作类型（add / update / remove / start / stop / trigger）
     * @param jobId      任务 id
     */
    public static void report(String operator, Object jobInfo, String action, String jobId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("operator", operator);
            payload.put("action", action);
            payload.put("jobId", jobId);

            // 将 XxlJobInfo 反射序列化为 JSON
            if (jobInfo != null) {
                Object jobInfoJson = GSON.toJsonTree(jobInfo);
                payload.put("jobInfo", jobInfoJson);
            }

            String json = GSON.toJson(payload);

            Consumer hook = testHook;
            if (hook != null) {
                // 测试模式：交给钩子处理
                System.out.println("[myagent] Reporting (test-hook) : " + json);
                hook.accept(json);
            } else {
                // 生产模式：真实 HTTP 请求
                System.out.println("[myagent] Reporting to " + REPORT_URL + " : " + json);
                //TODO zyl 测试暂时注释
//                doPost(json);
            }

        } catch (Exception e) {
            System.err.println("[myagent] Report failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 发送 POST 请求（JSON body）。
     */
    private static void doPost(String json) throws Exception {
        URL url = new URL(REPORT_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            os.write(bytes);
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        System.out.println("[myagent] Report response code: " + responseCode);
        conn.disconnect();
    }
}
