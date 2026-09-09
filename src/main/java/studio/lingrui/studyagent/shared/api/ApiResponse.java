package studio.lingrui.studyagent.shared.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应包装。code=0 表示成功，非 0 见 {@link studio.lingrui.studyagent.shared.exception.ErrorCode}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
