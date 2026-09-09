package studio.lingrui.studyagent.application.agent;

import studio.lingrui.studyagent.domain.rag.RetrievedChunk;

import java.util.List;

/**
 * 单 Agent 的提示词模板：统一的教学 Agent 人设 + 各生成任务的指令。
 */
public final class AIPrompts {

    private AIPrompts() {
    }

    /** 教学 Agent 人设（基础） */
    public static final String TUTOR_BASE = """
            你是一位耐心、专业、擅长启发式教学的个人「学习 Agent」。
            你的学习者是一个学生，请始终用简体中文回答，输出适合阅读的 Markdown。
            行为准则：
            1. 先判断问题属于：知识答疑 / 题目讲解 / 学习计划 / 知识整理 / 学习提醒。
            2. 讲解优先采用苏格拉底式引导：对概念先给直观解释，必要时反问确认学生理解。
            3. 基于个人知识库与上下文作答；不确定或资料不足时明确说明，不要编造。
            4. 涉及题目时给出完整解析；若学生答错，建议将其记录为错题以便复习。
            5. 语气友好、结构清晰，适当使用小标题、列表与公式说明（LaTeX）。
            """;

    /**
     * 组装带 RAG 上下文的系统提示。
     */
    public static String tutorSystem(List<RetrievedChunk> hits) {
        StringBuilder sb = new StringBuilder(TUTOR_BASE);
        if (hits == null || hits.isEmpty()) {
            sb.append("""

                    当前没有可用的个人知识库资料：可以结合通用知识回答，但应提示用户上传课件/课程表等资料可获得更贴合本人的讲解。
                    """);
        } else {
            sb.append("""

                    以下是【个人知识库检索结果】（按相关性排序，来自用户的课程表/课件/题目等资料），
                    回答应优先引用这些内容，并按需标注来源文档名（如「据《xxx》」）：
                    """);
            int idx = 1;
            for (RetrievedChunk c : hits) {
                sb.append("\n[资料").append(idx++).append("] 来源《")
                        .append(c.docName() == null ? "未知文档" : c.docName())
                        .append("》\n")
                        .append(c.text());
            }
            sb.append("""

                    引用规则：引用上述资料时说明出处；若资料与问题无关则忽略并提示。
                    """);
        }
        return sb.toString();
    }

    /** 从聊天会话自动生成学习笔记 */
    public static String noteFromConversation(String conversationText) {
        return """
                请把下面这段「学生与学习 Agent 的问答记录」整理成一份结构清晰的学习笔记（Markdown）。
                要求：
                - 提炼知识点，按主题分节，去掉寒暄与重复
                - 补充关键结论、易错点与后续复习建议
                - 控制在 500 字以内，中文

                问答记录如下：
                """ + conversationText;
    }

    /** 从文档自动生成学习整理 */
    public static String noteFromDocument(String docName, String excerpt) {
        return """
                下面是你需要整理的个人学习资料《%s》的文本摘录。
                请生成一份「学习整理笔记」（Markdown），要求：
                - 先给 2-3 行内容概要
                - 按逻辑分节归纳核心知识点，保留公式/示例要点
                - 标出值得记忆的结论与易混淆点
                - 最后给 3 条基于该内容的复习/自测建议
                - 中文，篇幅适中（800 字以内）

                资料摘录：
                %s
                """.formatted(docName, excerpt);
    }

    /** 从文档/学习资料自动出题 */
    public static String questionsFromDocument(String docName, String excerpt) {
        return """
                请基于学习资料《%s》生成 5 道自测题用于巩固（中文）。
                只输出 JSON，不要任何解释或 Markdown 代码块标记。JSON 格式：
                {"questions":[{"type":"SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|FILL_BLANK|SHORT_ANSWER",
                "stem":"题干","options":["选项A","选项B","选项C","选项D"]（选择题必填，其他类型可省略）,
                "answer":"答案：单选题填 A；多选题填 A,B；判断题填 对/错；填空与简答填参考答案文本",
                "explanation":"解析","difficulty":3,"tags":["知识点标签"]}]}
                要求：覆盖资料主要知识点；难度 1~5；不要生成资料之外的内容。

                资料摘录：
                %s
                """.formatted(docName, excerpt);
    }

    /** 学习分析总结 */
    public static String learningAnalysisSummary(String statsText) {
        return """
                请基于下面的学习数据统计，为这位学生写一份简短的学习分析（200 字内，中文，Markdown）：
                指出 2 个优势、2 个薄弱环节，并给出可执行的下一步建议（可提及错题复习与计划执行）。

                统计数据：
                %s
                """.formatted(statsText);
    }
}
