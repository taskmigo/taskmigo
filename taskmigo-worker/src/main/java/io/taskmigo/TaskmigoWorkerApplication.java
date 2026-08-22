package io.taskmigo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Taskmigo Worker")
@SpringBootApplication
public class TaskmigoWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskmigoWorkerApplication.class, args);
    }
}
