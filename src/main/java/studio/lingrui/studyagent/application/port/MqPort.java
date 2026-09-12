package studio.lingrui.studyagent.application.port;

/**
 * 消息队列端口：应用层只声明"要派发哪些领域事件"，不依赖 RabbitMQ 的具体类型。
 *
 * <p>注意实现是可选的（{@code study-agent.mq.enabled=false} 时不存在对应 Bean），
 * 应用服务需通过 {@code ObjectProvider} 注入并在缺失时走同步回退
 * ——"没有 MQ 也能完整跑通"是这条链路的设计前提。
 */
public interface MqPort {

    /** 派发文档摄入任务 */
    void publishDocumentIngest(Long docId);

    /** 派发学习提醒推送事件 */
    void publishReminderPush(Long userId, Long reminderId, String title, String message);
}
