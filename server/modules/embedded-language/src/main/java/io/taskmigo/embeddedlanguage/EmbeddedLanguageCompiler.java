package io.taskmigo.embeddedlanguage;

import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageLexer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

/// Compiles direct-body Embedded Language source into typed immutable IR.
///
/// The generated ANTLR lexer supplies the canonical tokenization. Syntax validation and semantic lowering are
/// performed by the allocation-conscious token frontend so ANTLR parse-tree objects are not created at runtime.
@SuppressWarnings("checkstyle:NeedBraces")
public final class EmbeddedLanguageCompiler {

    private final CompilerLimits limits;
    private final String compilerFingerprint;

    /// Creates a compiler with the finite default contract limits.
    public EmbeddedLanguageCompiler() {
        this(CompilerLimits.defaults());
    }

    /// Creates a compiler with explicit finite limits.
    public EmbeddedLanguageCompiler(CompilerLimits limits) {
        this.limits = Objects.requireNonNull(limits);
        this.compilerFingerprint = this.limits.fingerprint() + ":" + LanguageIr.LANGUAGE_VERSION;
    }

    /// Returns the identity of the compiler limits and language contract.
    public String contractFingerprint() {
        return this.compilerFingerprint;
    }

    /// Compiles source against a consumer-owned environment schema.
    public LanguageIr compile(String source, EnvironmentSchema schema) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(schema);
        if (source.length() > this.limits.maxSourceCharacters()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program source exceeds the source-size limit",
                unknown()
            );
        }

        Errors errors = new Errors();
        EmbeddedLanguageLexer lexer = new EmbeddedLanguageLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        if (!errors.diagnostics.isEmpty()) throw new EmbeddedLanguageException(errors.diagnostics);
        if (tokens.size() - 1 > this.limits.maxTokens()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program token count exceeds the token limit",
                unknown()
            );
        }
        if (syntaxDepth(tokens) > this.limits.maxSyntaxDepth()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program syntax depth exceeds the limit",
                unknown()
            );
        }

        LanguageIr.Expression expression = new FastEmbeddedLanguageCompiler(schema, this.limits, tokens).compile();
        if (expression.type() != LanguageType.Scalar.BOOL) {
            throw failure(LanguageDiagnostic.Category.TypeError, "program result must be Bool", expression.span());
        }
        return new LanguageIr(
            expression,
            fingerprint(source),
            LanguageIr.LANGUAGE_VERSION,
            schema.fingerprint(),
            compilerFingerprint
        );
    }

    private static String fingerprint(String source) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static int syntaxDepth(CommonTokenStream tokens) {
        int depth = 0;
        int maximum = 0;
        for (Token token : tokens.getTokens()) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == Token.EOF) continue;
            switch (token.getType()) {
                case
                    EmbeddedLanguageLexer.LBRACE,
                    EmbeddedLanguageLexer.LBRACKET,
                    EmbeddedLanguageLexer.LPAREN -> maximum = Math.max(maximum, ++depth);
                case
                    EmbeddedLanguageLexer.RBRACE,
                    EmbeddedLanguageLexer.RBRACKET,
                    EmbeddedLanguageLexer.RPAREN -> depth--;
                default -> {
                }
            }
        }
        return maximum;
    }

    static EmbeddedLanguageException failure(
        LanguageDiagnostic.Category category,
        String message,
        LanguageDiagnostic.SourceSpan span
    ) {
        return new EmbeddedLanguageException(new LanguageDiagnostic(category, message, span));
    }

    private static LanguageDiagnostic.SourceSpan unknown() {
        return new LanguageDiagnostic.SourceSpan(1, 0, 1, 0);
    }

    private static final class Errors extends BaseErrorListener {

        private final List<LanguageDiagnostic> diagnostics = new ArrayList<>();

        @Override
        public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int column,
            String message,
            @Nullable RecognitionException exception
        ) {
            this.diagnostics.add(
                new LanguageDiagnostic(
                    LanguageDiagnostic.Category.SyntaxError,
                    message,
                    new LanguageDiagnostic.SourceSpan(line, column, line, column + 1)
                )
            );
        }
    }
}
