package studio.lingrui.studyagent.domain.rag;

/**
 * 文档索引状态。
 */
public enum IndexStatus {
    /** 待处理（已入库未解析） */
    PENDING,
    /** 解析/向量化中 */
    PROCESSING,
    /** 已索引（可检索） */
    INDEXED,
    /** 失败 */
    FAILED
}
