package io.taskmigo.buildlogic;

import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

public final class InternalModuleBoundariesPlugin implements Plugin<Project> {

    static final String INTERNAL_MODULE_PROJECT_PREFIX = ":modules:internal:";

    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new GradleException("io.taskmigo.internal-module-boundaries must be applied to the root project");
        }

        TaskProvider<Task> dependencyCheck = project.getTasks().register("verifyInternalProjectDependencies", task -> {
            task.setGroup("verification");
            task.setDescription("Rejects forbidden direct Gradle dependencies on internal server modules.");
            task.doLast(ignored -> InternalDependencyVerifier.verify(project));
        });
        TaskProvider<Task> importCheck = project.getTasks().register("verifyApplicationInternalImports", task -> {
            task.setGroup("verification");
            task.setDescription("Rejects production app source imports from internal server modules.");
            task.doLast(ignored -> AppInternalImportVerifier.verify(project));
        });
        TaskProvider<Task> apiCheck = registerInternalApiCheck(project);
        TaskProvider<Task> boundaryCheck = project.getTasks().register("verifyInternalModuleBoundaries", task -> {
            task.setGroup("verification");
            task.setDescription("Verifies all internal server module boundaries.");
            task.dependsOn(dependencyCheck, importCheck, apiCheck);
        });

        project.getPluginManager().withPlugin("base", ignored ->
            project
                .getTasks()
                .named("check")
                .configure(task -> task.dependsOn(boundaryCheck))
        );
    }

    static boolean isApp(Project project) {
        return project.getPath().startsWith(":apps:");
    }

    static boolean isInternalModule(Project project) {
        return project.getPath().startsWith(INTERNAL_MODULE_PROJECT_PREFIX);
    }

    static boolean isProductionModule(Project project) {
        return project.getPath().startsWith(":modules:") && !isInternalModule(project);
    }

    private static TaskProvider<Task> registerInternalApiCheck(Project project) {
        TaskProvider<Task> apiCheck = project.getTasks().register("verifyInternalApiBoundaries", task -> {
            task.setGroup("verification");
            task.setDescription(
                "Rejects internal module types from public application APIs and Modulith named interfaces."
            );
            task.doLast(ignored -> InternalApiLeakVerifier.verify(project));
        });

        project.getGradle().projectsEvaluated(ignored -> {
            List<TaskProvider<Jar>> jarTasks = project
                .getSubprojects()
                .stream()
                .filter(InternalModuleBoundariesPlugin::isProductionModule)
                .filter(module -> module.getPluginManager().hasPlugin("java"))
                .map(module -> module.getTasks().named("jar", Jar.class))
                .toList();
            apiCheck.configure(task -> task.dependsOn(jarTasks));
        });

        return apiCheck;
    }
}
