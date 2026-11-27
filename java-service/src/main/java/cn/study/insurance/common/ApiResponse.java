package cn.study.insurance.common;

/**
 * 统一响应结构，供新增的业务接口使用。
 *
 * 说明：products 系列接口保持原始数组/对象格式不变。
 * 原因是前端和 Python Agent 都直接按裸 JSON 解析，包一层会破坏已有契约。
 * 认证、方案和保单接口统一用这个结构，前端按 code 判断成败。
 */
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "success", null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
