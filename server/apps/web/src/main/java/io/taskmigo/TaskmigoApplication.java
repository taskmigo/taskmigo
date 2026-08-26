package io.taskmigo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Taskmigo Web")
@SpringBootApplication
public class TaskmigoApplication {

    static void main(String[] args) {
        SpringApplication.run(TaskmigoApplication.class, args);
    }
}
