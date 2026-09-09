package studio.lingrui.studyagent.application.plan;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.lingrui.studyagent.domain.plan.StudyReminder;
import studio.lingrui.studyagent.domain.plan.StudyReminderRepository;
import studio.lingrui.studyagent.infrastructure.cache.RedisCacheHelper;
import studio.lingrui.studyagent.infrastructure.config.properties.StudyAgentProperties;
import studio.lingrui.studyagent.infrastructure.mq.MqGateway;
import studio.lingrui.studyagent.shared.exception.BizException;
import studio.lingrui.studyagent.shared.exception.ErrorCode;

import java.util.List;

/**
 * 学习提醒（应用服务）：提醒落库 + 可选 MQ 推送 + Redis 缓存未读数。
 */
@Service
@RequiredArgsConstructor
public class ReminderApplicationService {

    private static final String UNREAD_PREFIX = "cache:reminderUnread:";

    private final StudyReminderRepository reminders;
    private final StudyAgentProperties props;
    private final ObjectProvider<MqGateway> mqGatewayProvider;
    private final RedisCacheHelper cache;

    public Page<StudyReminder> list(Long userId, int page, int size) {
        return reminders.findByUserIdOrderByIdDesc(userId, PageRequest.of(page - 1, size));
    }

    public List<StudyReminder> unread(Long userId) {
        return reminders.findByUserIdAndReadFlagFalseOrderByIdAsc(userId);
    }

    public long unreadCount(Long userId) {
        String cacheKey = UNREAD_PREFIX + userId;
        Long cached = cache.get(cacheKey, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }
        long count = reminders.countByUserIdAndReadFlagFalse(userId);
        cache.set(cacheKey, count, props.getCache().getDefaultTtl());
        return count;
    }

    @Transactional
    public void markRead(Long userId, Long reminderId) {
        StudyReminder r = reminders.findByIdAndUserId(reminderId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "提醒不存在"));
        r.markRead();
        reminders.save(r);
        evictUnread(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        reminders.findByUserIdAndReadFlagFalseOrderByIdAsc(userId).forEach(r -> {
            r.markRead();
            reminders.save(r);
        });
        evictUnread(userId);
    }

    /**
     * 供调度器写入提醒并推送（写入成功后再推送，失败仅告警）。
     */
    public void pushReminder(StudyReminder reminder) {
        StudyReminder saved = reminders.save(reminder);
        evictUnread(saved.getUserId());
        MqGateway mq = mqGatewayProvider.getIfAvailable();
        if (props.getMq().isEnabled() && mq != null) {
            mq.publishReminderPush(saved.getUserId(), saved.getId(),
                    saved.getTitle(), saved.getMessage());
        }
    }

    private void evictUnread(Long userId) {
        if (userId != null) {
            cache.delete(UNREAD_PREFIX + userId);
        }
    }
}
