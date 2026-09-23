package io.taskmigo.authorization.embeddedlanguage;

import io.taskmigo.language.CompilationFeature;
import io.taskmigo.language.CompilationMode;
import io.taskmigo.language.CompilationProfile;
import java.util.EnumSet;

/// Defines the bounded Language feature contracts for authorization policies.
public final class AuthorizationCompilationProfile {

    private static final CompilationProfile REQUEST_POLICY = new CompilationProfile(
        CompilationMode.PROGRAM,
        EnumSet.allOf(CompilationFeature.class)
    );
    private static final CompilationProfile OBJECT_POLICY = new CompilationProfile(
        CompilationMode.EXPRESSION,
        EnumSet.of(
            CompilationFeature.LIST_LITERALS,
            CompilationFeature.MEMBERSHIP,
            CompilationFeature.LOGICAL_OPERATORS,
            CompilationFeature.EQUALITY_OPERATORS,
            CompilationFeature.ORDERING_OPERATORS,
            CompilationFeature.ARITHMETIC_OPERATORS,
            CompilationFeature.COLLECTION_QUANTIFIERS,
            CompilationFeature.LENGTH_INTRINSIC
        )
    );

    private AuthorizationCompilationProfile() {}

    /// Returns the program profile used for Request Authorization policies.
    public static CompilationProfile requestPolicy() {
        return REQUEST_POLICY;
    }

    /// Returns the expression profile used for Object Authorization policies.
    public static CompilationProfile objectPolicy() {
        return OBJECT_POLICY;
    }
}
