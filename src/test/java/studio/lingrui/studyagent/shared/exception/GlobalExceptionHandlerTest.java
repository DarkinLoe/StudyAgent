package studio.lingrui.studyagent.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import studio.lingrui.studyagent.shared.api.ApiResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 异常 → HTTP 状态映射测试。
 * 重点是"乐观锁冲突必须是 409 而不是 500"：BaseEntity 上所有聚合都带 @Version，
 * 并发冲突是正常业务语义，调用方需要据此判断能否重试。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void bizNotFoundMapsTo404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBiz(new BizException(ErrorCode.SESSION_NOT_FOUND, "会话不存在"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ErrorCode.SESSION_NOT_FOUND.getCode(), body(response).getCode());
    }

    @Test
    void aiUnavailableMapsTo503() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBiz(new BizException(ErrorCode.AI_UNAVAILABLE, "AI 暂不可用"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void optimisticLockConflictMapsTo409() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(Object.class, 1L));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(ErrorCode.CONFLICT.getCode(), body(response).getCode());
    }

    @Test
    void dataIntegrityViolationMapsTo409() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("uk_user_username"));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(ErrorCode.CONFLICT.getCode(), body(response).getCode());
    }

    private ApiResponse<Void> body(ResponseEntity<ApiResponse<Void>> response) {
        ApiResponse<Void> body = response.getBody();
        assertNotNull(body, "响应体不应为空");
        return body;
    }
}
