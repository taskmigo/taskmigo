package io.taskmigo.authorization.embeddedlanguage;

import io.taskmigo.language.CompilationFeature;
import io.taskmigo.language.CompilationMode;
import io.taskmigo.language.CompilationProfile;
import java.util.EnumSet;
import java.util.Set;

/// Defines the bounded Language feature contracts for authorization policies.
public final class AuthorizationCompilationProfile {

    private static final Set<CompilationFeature> EXPRESSION_POLICY_FEATURES = Set.of(
        CompilationFeature.LIST_LITERALS,
        CompilationFeature.MEMBERSHIP,
        CompilationFeature.LOGICAL_OPERATORS,
        CompilationFeature.EQUALITY_OPERATORS,
        CompilationFeature.ORDERING_OPERATORS,
        CompilationFeature.ARITHMETIC_OPERATORS,
        CompilationFeature.COLLECTION_QUANTIFIERS,
        CompilationFeature.LENGTH_INTRINSIC
    );
    private static final CompilationProfile REQUEST_POLICY = new CompilationProfile(
        CompilationMode.PROGRAM,
        requestPolicyFeatures()
    );
    private static final CompilationProfile OBJECT_POLICY = new CompilationProfile(
        CompilationMode.EXPRESSION,
        EXPRESSION_POLICY_FEATURES
    );

    private AuthorizationCompilationProfile() {}

    private static Set<CompilationFeature> requestPolicyFeatures() {
        EnumSet<CompilationFeature> features = EnumSet.copyOf(EXPRESSION_POLICY_FEATURES);
        features.add(CompilationFeature.LOCAL_BINDINGS);
        features.add(CompilationFeature.CONDITIONAL_CONTROL_FLOW);
        return features;
    }

    /// Returns the program profile used for Request Authorization policies.
    public static CompilationProfile requestPolicy() {
        return REQUEST_POLICY;
    }

    /// Returns the expression profile used for Object Authorization policies.
    public static CompilationProfile objectPolicy() {
        return OBJECT_POLICY;
    }
}
