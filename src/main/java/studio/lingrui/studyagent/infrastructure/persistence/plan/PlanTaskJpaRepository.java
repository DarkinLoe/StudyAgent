package studio.lingrui.studyagent.infrastructure.persistence.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.plan.PlanTask;
import studio.lingrui.studyagent.domain.plan.PlanTaskRepository;

@Repository
public interface PlanTaskJpaRepository
        extends JpaRepository<PlanTask, Long>, PlanTaskRepository {
}
