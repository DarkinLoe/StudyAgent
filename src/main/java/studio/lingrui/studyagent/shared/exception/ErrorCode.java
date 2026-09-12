package studio.lingrui.studyagent.shared.exception;

import lombok.Getter;

/**
 * 业务错误码。code=0 成功；1xxx 通用；2xxx 各域业务错误。
 */
@Getter
public enum ErrorCode {

    OK(0, "成功"),

    // 通用
    BAD_REQUEST(1000, "请求参数错误"),
    UNAUTHORIZED(1001, "未认证或登录已过期"),
    FORBIDDEN(1002, "无权限访问"),
    NOT_FOUND(1004, "资源不存在"),
    CONFLICT(1005, "资源状态冲突"),
    TOO_MANY_REQUESTS(1006, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(1500, "系统内部错误"),

    // 知识库 / RAG
    DOC_NOT_FOUND(2001, "知识文档不存在"),
    DOC_UNSUPPORTED_TYPE(2002, "不支持的文件类型"),
    DOC_PARSE_FAILED(2003, "文档解析失败"),
    DOC_NOT_INDEXED(2004, "文档尚未完成索引"),
    RAG_RETRIEVAL_FAILED(2005, "知识检索失败"),

    // 题库 / 错题
    QUESTION_NOT_FOUND(2101, "题目不存在"),
    QUESTION_INVALID(2102, "题目内容不合法（选项/答案缺失或与题型不匹配）"),
    WRONG_RECORD_NOT_FOUND(2103, "错题记录不存在"),

    // 会话 / 问答
    SESSION_NOT_FOUND(2201, "会话不存在"),
    AI_UNAVAILABLE(2202, "AI 服务暂不可用，请检查 API Key 与网络"),

    // 学习计划 / 提醒
    PLAN_NOT_FOUND(2301, "学习计划不存在"),
    PLAN_TASK_NOT_FOUND(2302, "计划任务不存在"),

    // 笔记 / 知识管理
    NOTE_NOT_FOUND(2401, "笔记不存在");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
