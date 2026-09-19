package io.taskmigo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Taskmigo Migration")
@SpringBootApplication
public class TaskmigoMigrationApplication {

    static void main(String[] args) {
        SpringApplication.run(TaskmigoMigrationApplication.class, args).close();
    }
}
