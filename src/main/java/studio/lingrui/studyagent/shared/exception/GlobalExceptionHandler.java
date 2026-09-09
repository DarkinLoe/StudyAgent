package studio.lingrui.studyagent.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import studio.lingrui.studyagent.shared.api.ApiResponse;

/**
 * 全局异常 → 统一响应体。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        HttpStatus status = switch (e.getErrorCode()) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, DOC_NOT_FOUND, QUESTION_NOT_FOUND, WRONG_RECORD_NOT_FOUND,
                 SESSION_NOT_FOUND, PLAN_NOT_FOUND, PLAN_TASK_NOT_FOUND, NOTE_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
            case CONFLICT, DOC_UNSUPPORTED_TYPE, DOC_PARSE_FAILED, DOC_NOT_INDEXED,
                 QUESTION_INVALID, RAG_RETRIEVAL_FAILED, AI_UNAVAILABLE ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        log.warn("业务异常: code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(e.getErrorCode().getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), msg));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), "请求参数格式错误: " + e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), "上传文件超过大小限制"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(), "系统内部错误"));
    }
}
