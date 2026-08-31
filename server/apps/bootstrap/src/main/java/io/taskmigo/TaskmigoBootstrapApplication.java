package io.taskmigo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaskmigoBootstrapApplication {

    static void main(String[] args) {
        SpringApplication.run(TaskmigoBootstrapApplication.class, args).close();
    }
}
