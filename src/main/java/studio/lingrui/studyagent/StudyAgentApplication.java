package studio.lingrui.studyagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StudyAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyAgentApplication.class, args);
    }

}
