package com.taskmigo.console;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.lang.syntax.elements.GivenMethodsConjunction;
import com.tngtech.archunit.lang.syntax.elements.MethodsShouldConjunction;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.security.web.SecurityFilterChain;

class ArchitectureTest {
  private final ApplicationModules modules = ApplicationModules.of(ConsoleApplication.class);

  ArchitectureTest() {}

  @Test
  void modulesHaveNoCyclesOrIllegalInternalDependencies() {
    this.modules.verify();
  }

  @Test
  void onlyFeatureOwnedModulesAreDetected() {
    Assertions.assertThat(this.modules.stream().map(module -> module.getIdentifier().toString()))
        .containsExactlyInAnyOrder(new String[] {"access", "system"});
    Assertions.assertThat((Optional) this.modules.getModuleByName("api")).isEmpty();
    Assertions.assertThat((Optional) this.modules.getModuleByName("identity")).isEmpty();
    Assertions.assertThat((Optional) this.modules.getModuleByName("authorization")).isEmpty();
    Assertions.assertThat((Optional) this.modules.getModuleByName("composition")).isEmpty();
  }

  @Test
  void autowiredAnnotationIsNotUsed() {
    ArchRuleDefinition.noClasses()
        .should()
        .beAnnotatedWith(Autowired.class)
        .check(new ClassFileImporter().importPackages(new String[] {"com.taskmigo.console"}));
  }

  @Test
  void accessModuleOwnsEverySecurityFilterChain() {
    ((MethodsShouldConjunction)
            ((GivenMethodsConjunction)
                    ArchRuleDefinition.methods()
                        .that()
                        .haveRawReturnType(SecurityFilterChain.class))
                .should()
                .beDeclaredInClassesThat()
                .resideInAPackage("..access.internal.configuration.."))
        .check(new ClassFileImporter().importPackages(new String[] {"com.taskmigo.console"}));
  }
}
