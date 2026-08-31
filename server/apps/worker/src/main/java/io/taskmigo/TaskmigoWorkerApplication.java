package io.taskmigo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@Modulithic(systemName = "Taskmigo Worker")
@EnableScheduling
@SpringBootApplication
public class TaskmigoWorkerApplication {

    static void main(String[] args) {
        SpringApplication.run(TaskmigoWorkerApplication.class, args);
    }
}
