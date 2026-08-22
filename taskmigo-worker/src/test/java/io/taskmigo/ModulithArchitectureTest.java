package io.taskmigo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {
    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(TaskmigoWorkerApplication.class).verify();
    }
}
