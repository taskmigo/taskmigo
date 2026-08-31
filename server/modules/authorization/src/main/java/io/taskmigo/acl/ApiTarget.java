package io.taskmigo.acl;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record ApiTarget(Set<String> methods, String path) {

    public ApiTarget {
        methods = methods.stream().map(method -> method.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!path.startsWith("/api/")) {
            throw new IllegalArgumentException("ACL targets must be API paths: " + path);
        }
    }

    public boolean matches(String method, String requestPath) {
        return methods.contains(method.toUpperCase(Locale.ROOT)) && Pattern.matches(toRegex(path), requestPath);
    }

    private static String toRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char character = glob.charAt(index);
            if (character == '*') {
                boolean doubleStar = index + 1 < glob.length() && glob.charAt(index + 1) == '*';
                regex.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) index++;
            } else if ("\\.[]{}()+-^$|?".indexOf(character) >= 0) {
                regex.append('\\').append(character);
            } else {
                regex.append(character);
            }
        }
        return regex.append('$').toString();
    }
}
