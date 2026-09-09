package studio.lingrui.studyagent.interfaces.rest.knowledge;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.lingrui.studyagent.application.analytics.LearningAnalyticsService;
import studio.lingrui.studyagent.application.analytics.LearningStats;
import studio.lingrui.studyagent.infrastructure.web.UserContext;
import studio.lingrui.studyagent.shared.api.ApiResponse;

import java.util.Map;

/**
 * 学习分析。
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final LearningAnalyticsService analyticsService;

    @GetMapping("/learning")
    public ApiResponse<LearningStats> learning() {
        return ApiResponse.ok(analyticsService.compute(UserContext.getUserId()));
    }

    /** AI 文字版学习分析（会调用大模型） */
    @GetMapping("/learning/summary")
    public ApiResponse<Map<String, String>> summary() {
        String text = analyticsService.summarize(UserContext.getUserId());
        return ApiResponse.ok(Map.of("summary", text));
    }
}
