package studio.lingrui.studyagent.domain.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.lingrui.studyagent.domain.common.BaseEntity;

/**
 * 学习笔记（聚合根）：手动整理或 Agent 自动生成的笔记/学习整理产物。
 */
@Getter
@Setter
@Entity
@Table(name = "study_note", indexes = @Index(name = "idx_note_user", columnList = "user_id"))
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StudyNote extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private NoteSourceType sourceType = NoteSourceType.MANUAL;

    /** 来源说明：如 sessionId/文档 id/生成上下文 */
    @Column(name = "source_ref", length = 200)
    private String sourceRef;

    /** 标签（JSON 字符串数组） */
    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    public static StudyNote create(Long userId, String title, String content,
                                   NoteSourceType sourceType, String sourceRef, String tagsJson) {
        StudyNote note = new StudyNote();
        note.setUserId(userId);
        note.setTitle(title);
        note.setContent(content);
        note.setSourceType(sourceType);
        note.setSourceRef(sourceRef);
        note.setTagsJson(tagsJson);
        return note;
    }

    public void edit(String title, String content, String tagsJson) {
        this.title = title;
        this.content = content;
        this.tagsJson = tagsJson;
    }
}
