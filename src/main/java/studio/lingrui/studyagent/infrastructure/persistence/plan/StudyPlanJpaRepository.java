package studio.lingrui.studyagent.infrastructure.persistence.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.lingrui.studyagent.domain.plan.StudyPlan;
import studio.lingrui.studyagent.domain.plan.StudyPlanRepository;

@Repository
public interface StudyPlanJpaRepository
        extends JpaRepository<StudyPlan, Long>, StudyPlanRepository {
}
