package io.taskmigo.auth.authorization.embeddedlanguage;

import io.taskmigo.embeddedlanguage.CompilationFeature;
import io.taskmigo.embeddedlanguage.CompilationMode;
import io.taskmigo.embeddedlanguage.CompilationProfile;
import java.util.EnumSet;

/// Defines the bounded Embedded Language feature contract for authorization policies.
public final class AuthorizationCompilationProfile {

    private AuthorizationCompilationProfile() {}

    /// Returns the authorization-owned profile used for Request and Object policies.
    public static CompilationProfile policy() {
        return new CompilationProfile(
            CompilationMode.PROGRAM,
            EnumSet.of(
                CompilationFeature.LOCAL_BINDINGS,
                CompilationFeature.CONDITIONAL_CONTROL_FLOW,
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
    }
}
