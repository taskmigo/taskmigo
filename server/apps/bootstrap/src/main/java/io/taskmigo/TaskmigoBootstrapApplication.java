package io.taskmigo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Taskmigo Bootstrap")
@SpringBootApplication
public class TaskmigoBootstrapApplication {

    static void main(String[] args) {
        SpringApplication.run(TaskmigoBootstrapApplication.class, args).close();
    }
}
