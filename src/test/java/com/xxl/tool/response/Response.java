package com.xxl.tool.response;

/**
 * Mock —— 对应真实 Response&lt;T&gt;。
 */
public class Response<T> {

    private T data;

    public Response() {}

    public Response(T data) {
        this.data = data;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
