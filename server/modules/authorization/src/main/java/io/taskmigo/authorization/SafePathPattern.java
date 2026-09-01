package io.taskmigo.authorization;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

final class SafePathPattern {

    private static final int MAX_LENGTH = 512;
    private final String source;
    private final Pattern pattern;

    private SafePathPattern(String source, Pattern pattern) {
        this.source = source;
        this.pattern = pattern;
    }

    static SafePathPattern compile(String source) {
        if (source.isBlank()) throw new IllegalArgumentException("match.path must not be blank");
        if (source.length() > MAX_LENGTH) throw new IllegalArgumentException("match.path exceeds 512 characters");
        String normalized = normalizeNonCapturingGroups(source);
        try {
            return new SafePathPattern(source, Pattern.compile(normalized));
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(
                "Invalid or unsupported match.path regex: " + exception.getDescription()
            );
        }
    }

    boolean matches(String path) {
        return this.pattern.matcher(path).matches();
    }

    String source() {
        return this.source;
    }

    private static String normalizeNonCapturingGroups(String source) {
        StringBuilder normalized = new StringBuilder(source.length());
        boolean escaped = false;
        boolean characterClass = false;
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (escaped) {
                normalized.append(current);
                escaped = false;
                index++;
            } else if (current == '\\') {
                normalized.append(current);
                escaped = true;
                index++;
            } else {
                if (current == '[') characterClass = true;
                if (current == ']') characterClass = false;
                if (!characterClass && source.startsWith("(?:", index)) {
                    normalized.append('(');
                    index += 3;
                } else {
                    normalized.append(current);
                    index++;
                }
            }
        }
        return normalized.toString();
    }
}
