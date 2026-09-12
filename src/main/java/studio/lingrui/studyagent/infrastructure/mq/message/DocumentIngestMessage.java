package studio.lingrui.studyagent.infrastructure.mq.message;

/**
 * 文档摄入任务消息（上传后派发 → 消费者解析并建索引）。
 *
 * <p>用明确的 DTO 而不是 {@code Map.of(...)}：Map 的运行时类型是
 * {@code java.util.ImmutableCollections$MapN}，会被 Jackson 写进 {@code __TypeId__} 头，
 * 消费端反序列化时既不稳定也不可读。
 */
public record DocumentIngestMessage(Long docId) {
}
