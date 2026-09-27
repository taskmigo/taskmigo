package io.taskmigo.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Taskmigo Migration")
@AutoConfigurationPackage(basePackages = "io.taskmigo")
@SpringBootApplication(scanBasePackages = "io.taskmigo")
public class TaskmigoMigrationApplication {

    static void main(String[] args) {
        SpringApplication.run(TaskmigoMigrationApplication.class, args).close();
    }
}
