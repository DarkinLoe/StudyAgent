package studio.lingrui.studyagent.application.plan;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 「今日学习」展示项。
 */
public record TodayTask(Long planId, String planTitle, Long taskId, String subject,
                        String content, LocalDate plannedDate, LocalTime plannedStart,
                        Integer plannedMinutes, Boolean done, Boolean overdue) {
}
