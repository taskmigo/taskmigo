package io.taskmigo.benchmarks.authorization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

final class LanguageBenchmarkCorpus {

    static final int CASE_COUNT = 1_000;
    private static final String EXPECTED_CORPUS_SHA256 =
        "55fa9fe1ac0618b077e5b103db3041c19d8a3dfa6a7af8cafd49b357170f6b0c";
    private static final Pattern LITERAL_PATTERN = Pattern.compile(
        "\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b"
    );
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    enum Complexity {
        SIMPLE,
        COMPLEX,
    }

    enum Mode {
        PROGRAM,
        EXPRESSION,
    }

    record BenchmarkCase(String id, String source) {}

    static BenchmarkCase caseAt(String complexity, String mode, int index) {
        return caseAt(Complexity.valueOf(complexity), Mode.valueOf(mode), index);
    }

    static BenchmarkCase caseAt(Complexity complexity, Mode mode, int index) {
        if (index < 0 || index >= CASE_COUNT) {
            throw new IllegalArgumentException("index");
        }
        String source = switch (complexity) {
            case SIMPLE -> switch (mode) {
                case PROGRAM -> simpleProgram(index);
                case EXPRESSION -> simpleExpression(index);
            };
            case COMPLEX -> switch (mode) {
                case PROGRAM -> complexProgram(index);
                case EXPRESSION -> complexExpression(index);
            };
        };
        String id =
            complexity.name().toLowerCase(Locale.ROOT) +
            "/" +
            mode.name().toLowerCase(Locale.ROOT) +
            "/" +
            String.format(Locale.ROOT, "%04d", index) +
            ".taskmigo";
        return new BenchmarkCase(id, source.strip() + "\n");
    }

    static List<BenchmarkCase> cases(String complexity, String mode, int count) {
        if (count < 1 || count > CASE_COUNT) {
            throw new IllegalArgumentException();
        }
        Complexity c = Complexity.valueOf(complexity);
        Mode m = Mode.valueOf(mode);
        return IntStream.range(0, count)
            .mapToObj(i -> caseAt(c, m, i))
            .toList();
    }

    static String structuralShape(String source) {
        return WHITESPACE_PATTERN.matcher(LITERAL_PATTERN.matcher(source).replaceAll("<LITERAL>"))
            .replaceAll(" ")
            .strip();
    }

    static int[] dimensions(int index) {
        return new int[] { index % 10, (index / 10) % 10, (index / 100) % 10 };
    }

    static String simpleAtom(int kind, int index) {
        int number = (index % 17) + 1;
        return switch (kind) {
            case 0 -> "request.version >= object.version + " + number;
            case 1 -> "object.score - " + number + " <= principal.level";
            case 2 -> "request.method == \"METHOD_" + String.format(Locale.ROOT, "%04d", index) + "\"";
            case 3 -> "object.status != \"STATE_" + String.format(Locale.ROOT, "%04d", index) + "\"";
            case 4 -> "principal.active == object.enabled";
            case 5 -> "!principal.active";
            case 6 -> "principal.rank in [object.rank, request.version, " + number + "]";
            case 7 -> "principal.role in [\"ADMIN\", object.kind, \"ROLE_" +
                String.format(Locale.ROOT, "%04d", index) +
                "\"]";
            case 8 -> "len([request.method, object.status, principal.role]) >= 2";
            case 9 -> "request.pathVariables.userId == object.ownerId";
            default -> throw new IllegalArgumentException();
        };
    }

    static String simpleExpression(int index) {
        int[] d = dimensions(index);
        String left = simpleAtom(d[0], index);
        String right = simpleAtom(d[1], (index * 37 + 11) % CASE_COUNT);
        return switch (d[2]) {
            case 0 -> "(" + left + ") && (" + right + ")";
            case 1 -> "(" + left + ") || (" + right + ")";
            case 2 -> "!(" + left + ") && (" + right + ")";
            case 3 -> "(" + left + ") && !(" + right + ")";
            case 4 -> "!(" + left + ") || (" + right + ")";
            case 5 -> "(" + left + ") || !(" + right + ")";
            case 6 -> "(" + left + ") == (" + right + ")";
            case 7 -> "(" + left + ") != (" + right + ")";
            case 8 -> "((" + left + ") && (" + right + ")) || principal.active";
            case 9 -> "((" + left + ") || (" + right + ")) && object.enabled";
            default -> throw new IllegalArgumentException();
        };
    }

    static String simpleGate(int kind, int index) {
        int number = (index % 13) + 1;
        return switch (kind) {
            case 0 -> "principal.active";
            case 1 -> "object.enabled";
            case 2 -> "request.version >= " + number;
            case 3 -> "request.method == \"METHOD_" + String.format(Locale.ROOT, "%04d", index) + "\"";
            case 4 -> "principal.role in [\"ADMIN\", \"ROLE_" + String.format(Locale.ROOT, "%04d", index) + "\"]";
            case 5 -> "len([request.method, object.status]) == 2";
            case 6 -> "object.score + " + number + " >= principal.level";
            case 7 -> "request.pathVariables.userId == object.ownerId";
            case 8 -> "!principal.active";
            case 9 -> "principal.active == object.enabled";
            default -> throw new IllegalArgumentException();
        };
    }

    static String simpleProgram(int index) {
        int[] d = dimensions(index);
        String gate = simpleGate(d[0], index);
        String condition = simpleAtom(d[1], (index * 17 + 7) % CASE_COUNT);
        String declaration = "const gate = " + gate + "; ";
        String p = switch (d[2]) {
            case 0 -> "return gate && (" + condition + ");";
            case 1 -> "return gate || (" + condition + ");";
            case 2 -> "if (gate) { return " + condition + "; } return false;";
            case 3 -> "if (" + condition + ") { return gate; } return object.enabled;";
            case 4 -> "if (gate && (" + condition + ")) { return true; } else { return false; }";
            case 5 -> "if (gate) { return " + condition + "; } else { return !(" + condition + "); }";
            case 6 -> "if (" + condition + ") { return principal.active; } return gate;";
            case 7 -> "if (!gate) { return object.enabled; } return " + condition + ";";
            case 8 -> "if (gate == (" + condition + ")) { return true; } return principal.active;";
            case 9 -> "if (gate != (" + condition + ")) { return object.enabled; } else { return principal.active; }";
            default -> throw new IllegalArgumentException();
        };
        return declaration + p;
    }

    static String complexCore(int kind, int index) {
        int number = (index % 19) + 1;
        int factor = (index % 5) + 2;
        String ix = String.format(Locale.ROOT, "%04d", index);
        return switch (kind) {
            case 0 -> "all([request.version, object.version, principal.rank], value => value + " +
                number +
                " >= request.sequence - " +
                factor +
                ")";
            case 1 -> "any([request.method, object.status, principal.role], value => value == \"VALUE_" +
                ix +
                "\" || value == object.kind)";
            case 2 -> "none([object.score, object.priority, principal.level], value => value % " +
                factor +
                " < " +
                number +
                ")";
            case 3 -> "all([0, 1, " +
                number +
                "], outer => any([request.version, object.version, " +
                number +
                "], inner => inner + outer >= request.sequence))";
            case 4 -> "len([[request.method, object.status], [principal.role, object.kind]]) == 2";
            case 5 -> "((-object.score + request.version * " +
                factor +
                ") / " +
                factor +
                ") >= (+principal.level - object.priority)";
            case 6 -> "((request.pathVariables.userId < object.ownerId) || (request.method >= object.status)) && principal.active";
            case 7 -> "(principal.active && !object.enabled) || (request.version == object.version && object.score != principal.level)";
            case 8 -> "(request.version + " +
                number +
                ") in [object.version + " +
                number +
                ", principal.rank * " +
                factor +
                ", request.sequence - " +
                factor +
                "]";
            case 9 -> "len(request.method) + len(object.status) > " +
                number +
                " && any([principal.role, object.kind], role => role != \"GUEST_" +
                ix +
                "\")";
            default -> throw new IllegalArgumentException();
        };
    }

    static String complexExpression(int index) {
        int[] d = dimensions(index);
        String l = complexCore(d[0], index);
        String r = complexCore(d[1], (index * 29 + 17) % CASE_COUNT);
        return switch (d[2]) {
            case 0 -> "(" + l + ") && (" + r + ")";
            case 1 -> "(" + l + ") || (" + r + ")";
            case 2 -> "!(" + l + ") && (" + r + ")";
            case 3 -> "(" + l + ") || !(" + r + ")";
            case 4 -> "(" + l + ") == (" + r + ")";
            case 5 -> "(" + l + ") != (" + r + ")";
            case 6 -> "((" + l + ") && (" + r + ")) || (principal.active && object.enabled)";
            case 7 -> "((" + l + ") || (" + r + ")) && (request.version >= object.version)";
            case 8 -> "all([" + l + ", " + r + "], flag => flag || principal.active)";
            case 9 -> "any([" + l + ", " + r + ", principal.active], flag => flag && object.enabled)";
            default -> throw new IllegalArgumentException();
        };
    }

    static String complexDeclarations(int kind, int index) {
        int n = (index % 23) + 1;
        int f = (index % 5) + 2;
        String ix = String.format(Locale.ROOT, "%04d", index);
        return switch (kind) {
            case 0 -> "const threshold = request.version + " +
                n +
                "; const labels = [request.method, object.status, \"VALUE_" +
                ix +
                "\"]; const gate = principal.active && object.enabled;";
            case 1 -> "const threshold = (object.score + " +
                n +
                ") * " +
                f +
                "; const labels = [principal.role, object.kind, \"ROLE_" +
                ix +
                "\"]; const gate = request.method in labels;";
            case 2 -> "const threshold = principal.level - object.priority + " +
                n +
                "; const labels = [request.pathVariables.userId, object.ownerId, \"USER_" +
                ix +
                "\"]; const gate = len(labels) >= 2;";
            case 3 -> "const threshold = request.sequence % " +
                f +
                " + " +
                n +
                "; const labels = [\"ADMIN\", principal.role, object.visibility]; const gate = principal.role in labels;";
            case 4 -> "const threshold = -object.rank + principal.rank + " +
                n +
                "; const labels = [object.status, request.method, \"STATE_" +
                ix +
                "\"]; const gate = none(labels, item => item == \"DELETED\");";
            case 5 -> "const threshold = (request.version + object.version) / " +
                f +
                "; const labels = [principal.kind, object.kind, \"KIND_" +
                ix +
                "\"]; const gate = all(labels, item => len(item) >= 0);";
            case 6 -> "const threshold = len([request.method, object.status, principal.role]) + " +
                n +
                "; const labels = [request.method, principal.role, object.status]; const gate = request.pathVariables.userId == object.ownerId;";
            case 7 -> "const threshold = +principal.rank + " +
                n +
                "; const labels = [\"ADMIN\", principal.role, object.kind]; const gate = any(labels, item => item == principal.role);";
            case 8 -> "const threshold = object.priority * " +
                f +
                " - " +
                n +
                "; const labels = [object.visibility, object.status, request.method]; const gate = !object.enabled || principal.active;";
            case 9 -> "const threshold = request.sequence + principal.version + object.version; const labels = [request.method, object.kind, principal.kind]; const gate = len(request.method) >= 1 && object.enabled;";
            default -> throw new IllegalArgumentException();
        };
    }

    static String complexCondition(int kind, int index) {
        int n = (index % 17) + 1;
        String ix = String.format(Locale.ROOT, "%04d", index);
        return switch (kind) {
            case 0 -> "gate && object.score >= threshold";
            case 1 -> "request.method in labels || principal.role in labels";
            case 2 -> "all([object.score, principal.level, threshold], value => value >= 0) && gate";
            case 3 -> "any(labels, label => label == object.status) || request.pathVariables.userId == object.ownerId";
            case 4 -> "none(labels, label => label == \"BLOCKED_" +
                ix +
                "\") && object.priority + threshold >= principal.rank";
            case 5 -> "all([0, 1], outer => any([request.version, object.version, threshold], inner => inner + outer >= threshold))";
            case 6 -> "len(labels) >= 2 && len(request.method) + len(object.status) > 0";
            case 7 -> "((request.version + threshold) * 2 >= object.version + principal.level) && gate";
            case 8 -> "!(request.method == object.status) && (principal.active || object.enabled)";
            case 9 -> "(threshold in [request.version + threshold, object.score + " + n + ", principal.rank]) || gate";
            default -> throw new IllegalArgumentException();
        };
    }

    static String complexProgram(int index) {
        int[] d = dimensions(index);
        String decl = complexDeclarations(d[0], index);
        String c = complexCondition(d[1], (index * 31 + 5) % CASE_COUNT);
        String a = complexCondition((d[1] + 3) % 10, (index * 47 + 13) % CASE_COUNT);
        String p = switch (d[2]) {
            case 0 -> "if (" +
                c +
                ") { return gate && object.enabled; } else { return principal.active || request.version >= threshold; }";
            case 1 -> "if (" +
                c +
                ") { if (gate) { return object.score >= threshold; } return principal.active; } return " +
                a +
                ";";
            case 2 -> "if (" +
                c +
                ") { return true; } else if (" +
                a +
                ") { return object.enabled; } else { return principal.active; }";
            case 3 -> "if (" +
                c +
                ") { const score = object.score + threshold; return score >= principal.level; } else { const roleMatch = principal.role in labels; return roleMatch && gate; }";
            case 4 -> "if (" +
                c +
                ") { return gate; } if (" +
                a +
                ") { return object.enabled; } return principal.active;";
            case 5 -> "if (" +
                c +
                ") { if (" +
                a +
                ") { return request.version + threshold >= object.version; } else { return gate && principal.active; } } else { return object.enabled || principal.active; }";
            case 6 -> "if (" +
                c +
                ") { return all([object.score, threshold], value => value >= 0); } else { return any(labels, label => label == principal.role) && gate; }";
            case 7 -> "if (" +
                c +
                ") { const effective = threshold + request.version; if (effective >= object.version) { return gate; } return object.enabled; } return " +
                a +
                ";";
            case 8 -> "if (gate) { if (" +
                c +
                ") { return true; } if (" +
                a +
                ") { return object.enabled; } return principal.active; } else { return request.version >= object.version; }";
            case 9 -> "if (" +
                c +
                ") { const accepted = any(labels, label => label == request.method); return accepted && gate; } else if (" +
                a +
                ") { const fallback = object.priority + threshold; return fallback >= principal.rank; } else { return none(labels, label => label == object.status); }";
            default -> throw new IllegalArgumentException();
        };
        return decl + " " + p;
    }

    static String digestAll() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (Complexity c : List.of(Complexity.SIMPLE, Complexity.COMPLEX)) {
            for (Mode m : List.of(Mode.PROGRAM, Mode.EXPRESSION)) {
                for (int i = 0; i < CASE_COUNT; i++) {
                    BenchmarkCase bc = caseAt(c, m, i);
                    md.update(bc.id().getBytes(StandardCharsets.UTF_8));
                    md.update((byte) 0);
                    md.update(bc.source().getBytes(StandardCharsets.UTF_8));
                    md.update((byte) 0);
                }
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    static void verifyContract() {
        for (Complexity c : Complexity.values()) {
            for (Mode m : Mode.values()) {
                Set<String> sources = new HashSet<>();
                Set<String> shapes = new HashSet<>();
                for (int i = 0; i < CASE_COUNT; i++) {
                    var bc = caseAt(c, m, i);
                    sources.add(bc.source());
                    shapes.add(structuralShape(bc.source()));
                }
                if (sources.size() != CASE_COUNT || shapes.size() != CASE_COUNT) {
                    throw new IllegalStateException(
                        "Invalid benchmark corpus " +
                            c +
                            "/" +
                            m +
                            ": sources=" +
                            sources.size() +
                            ", structuralShapes=" +
                            shapes.size()
                    );
                }
            }
        }
        String actualDigest = digestAll();
        if (!EXPECTED_CORPUS_SHA256.equals(actualDigest)) {
            throw new IllegalStateException(
                "Benchmark corpus changed: expected SHA-256 " +
                    EXPECTED_CORPUS_SHA256 +
                    " but generated " +
                    actualDigest +
                    ". Review the generator change before updating the golden digest."
            );
        }
    }

    static {
        verifyContract();
    }
}
