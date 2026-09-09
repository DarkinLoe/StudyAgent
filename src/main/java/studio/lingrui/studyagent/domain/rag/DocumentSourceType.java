package studio.lingrui.studyagent.domain.rag;

/**
 * 知识文档来源类型。
 */
public enum DocumentSourceType {
    /** 课程表 */
    COURSE_TABLE,
    /** 学习安排 */
    STUDY_ARRANGEMENT,
    /** PPT/课件 */
    PPT,
    /** 题目资料 */
    QUESTION_BANK,
    /** 笔记资料 */
    NOTE,
    /** 其他 */
    OTHER
}
