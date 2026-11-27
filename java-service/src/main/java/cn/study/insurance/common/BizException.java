package cn.study.insurance.common;

/**
 * 业务异常。
 *
 * 业务代码里直接 throw new BizException("方案不存在")，
 * 由全局异常处理器统一转成 HTTP 状态码 + ApiResponse，Controller 里不用写 try/catch。
 */
public class BizException extends RuntimeException {

    private final int status;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public static BizException conflict(String message) {
        return new BizException(409, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    public int getStatus() { return status; }
}
