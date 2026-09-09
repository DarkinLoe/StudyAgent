package studio.lingrui.studyagent.application.analytics;

import java.util.List;

/**
 * 学习分析统计结果。
 */
public record LearningStats(
        long planTotal,
        long planActive,
        long taskTotal,
        long taskDone,
        double completionRate,
        long plannedMinutes,
        long doneMinutes,
        long doneMinutesLast7,
        long wrongTotal,
        long wrongPending,
        long wrongReviewed,
        long wrongMastered,
        long noteTotal,
        List<WeakTag> weakTags
) {

    /** 弱项知识点 */
    public record WeakTag(String name, long count) {
    }

    /** 供 LLM 总结的纯文本描述 */
    public String describe() {
        return """
                学习计划：共 %d 个（进行中 %d 个）
                计划任务：共 %d 个，已完成 %d 个，完成率 %.1f%%
                学习时长：计划 %d 分钟，已投入 %d 分钟，最近 7 天投入 %d 分钟
                错题本：共 %d 条（待复习 %d / 已复习 %d / 已掌握 %d）
                学习笔记：%d 篇
                高频错题知识点：%s
                """.formatted(
                planTotal, planActive,
                taskTotal, taskDone, completionRate * 100,
                plannedMinutes, doneMinutes, doneMinutesLast7,
                wrongTotal, wrongPending, wrongReviewed, wrongMastered,
                noteTotal,
                weakTags.isEmpty() ? "（暂无）"
                        : weakTags.stream().map(t -> t.name() + "×" + t.count()).toList());
    }
}
