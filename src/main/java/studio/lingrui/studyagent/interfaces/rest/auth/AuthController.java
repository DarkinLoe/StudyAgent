package studio.lingrui.studyagent.interfaces.rest.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.lingrui.studyagent.application.auth.AuthResult;
import studio.lingrui.studyagent.application.auth.AuthService;
import studio.lingrui.studyagent.application.auth.UserSnapshot;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;

/**
 * 认证接口：注册 / 登录 / 当前用户 / 登出。
 * 前端拿到 token 后，以 {@code Authorization: Bearer <token>} 访问其它接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<AuthResult> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(
                request.username(), request.password(), request.nickname()));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    /**
     * 登出：把当前令牌加入吊销名单。
     * 无状态 JWT 在有效期内不会自己失效，只清前端本地存储的话，
     * 被拿走的 token 依然能用到过期，所以登出必须打到服务端。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = (authorization != null && authorization.startsWith(BEARER_PREFIX))
                ? authorization.substring(BEARER_PREFIX.length()).trim()
                : null;
        authService.logout(token);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<MeView> me() {
        UserSnapshot user = authService.require(UserContext.getUserId());
        return ApiResponse.ok(new MeView(user.id(), user.username(), user.nickname()));
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            @Size(max = 64) String nickname) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record MeView(Long userId, String username, String nickname) {
    }
}
