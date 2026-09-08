package io.taskmigo.embeddedlanguage;

import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageLexer;
import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageParser;
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

/// Compiles direct-body Embedded Language source into an immutable typed Semantic AST.
///
/// The generated ANTLR lexer and parser implement the canonical grammar. The generated parse tree is converted into
/// the language-owned Semantic AST before evaluation or partial evaluation.
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
        this.compilerFingerprint = this.limits.fingerprint() + ":" + SemanticAst.LANGUAGE_VERSION;
    }

    /// Returns the identity of the compiler limits and language contract.
    public String contractFingerprint() {
        return this.compilerFingerprint;
    }

    /// Compiles source against a consumer-owned environment schema.
    public SemanticAst compile(String source, EnvironmentSchema schema) {
        return this.compile(source, schema, CompilationProfile.program());
    }

    /// Compiles source against a schema and explicit language compilation profile.
    public SemanticAst compile(String source, EnvironmentSchema schema, CompilationProfile profile) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(schema);
        Objects.requireNonNull(profile);
        if (source.length() > this.limits.maxSourceCharacters()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "source exceeds the source-size limit",
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

        EmbeddedLanguageParser parser = new EmbeddedLanguageParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        LanguageCompilerVisitor visitor = new LanguageCompilerVisitor(schema, this.limits, profile);
        SemanticAst.Expression expression;
        if (profile.mode() == CompilationMode.PROGRAM) {
            EmbeddedLanguageParser.ProgramContext program = parser.program();
            if (!errors.diagnostics.isEmpty()) throw new EmbeddedLanguageException(errors.diagnostics);
            expression = visitor.compile(program);
        } else {
            EmbeddedLanguageParser.ExpressionSourceContext expressionSource = parser.expressionSource();
            if (!errors.diagnostics.isEmpty()) throw new EmbeddedLanguageException(errors.diagnostics);
            expression = visitor.compile(expressionSource);
        }
        return new SemanticAst(
            expression,
            fingerprint(source),
            SemanticAst.LANGUAGE_VERSION,
            schema.fingerprint(),
            compilerFingerprint,
            profile.mode(),
            profile.fingerprint()
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
