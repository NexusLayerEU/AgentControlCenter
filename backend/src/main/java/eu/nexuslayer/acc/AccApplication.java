package eu.nexuslayer.acc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import eu.nexuslayer.acc.config.AccProperties;

@SpringBootApplication
@EnableConfigurationProperties(AccProperties.class)
@EnableScheduling
public class AccApplication {

    public static void main(String[] args) {
        // The SQLite file lives under ACC_HOME; make sure the directory exists
        // before Spring tries to open a datasource pointed at it.
        AccPaths.ensureHome();
        SpringApplication.run(AccApplication.class, args);
    }
}
