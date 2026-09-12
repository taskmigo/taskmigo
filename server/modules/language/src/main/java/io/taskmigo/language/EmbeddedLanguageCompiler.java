package io.taskmigo.language;

import io.taskmigo.language.antlr.EmbeddedLanguageLexer;
import io.taskmigo.language.antlr.EmbeddedLanguageParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;
import org.jspecify.annotations.Nullable;

/// Compiles direct-body Embedded Language source into an immutable typed Semantic AST.
@SuppressWarnings("checkstyle:NeedBraces")
final class EmbeddedLanguageCompiler {

    private static final String COMPILER_CONTRACT = "semantic-v2";

    private final CompilerLimits limits;
    private final String compilerFingerprint;

    EmbeddedLanguageCompiler() {
        this(CompilerLimits.defaults());
    }

    EmbeddedLanguageCompiler(CompilerLimits limits) {
        this.limits = Objects.requireNonNull(limits);
        this.compilerFingerprint = this.limits.fingerprint() + ":" + LanguageContract.VERSION + ":" + COMPILER_CONTRACT;
    }

    String contractFingerprint() {
        return this.compilerFingerprint;
    }

    SemanticAst compile(String source, EnvironmentSchema schema) {
        return this.compile(source, schema, CompilationProfile.program());
    }

    SemanticAst compile(String source, EnvironmentSchema schema, CompilationProfile profile) {
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
        BoundedTokenSource bounded = new BoundedTokenSource(lexer);
        CommonTokenStream tokens = new CommonTokenStream(bounded);
        tokens.fill();
        if (!errors.diagnostics.isEmpty()) throw new EmbeddedLanguageException(errors.diagnostics);
        if (bounded.tokenCount() > this.limits.maxTokens()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program token count exceeds the token limit",
                unknown()
            );
        }
        if (bounded.maximumDepth() > this.limits.maxSyntaxDepth()) {
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
            schema.fingerprint(),
            this.compilerFingerprint,
            profile.mode(),
            profile.fingerprint(),
            schema.rootCount(),
            visitor.localSlotCount(),
            RequiredRoots.from(expression)
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

    private static final class BoundedTokenSource implements TokenSource {

        private final TokenSource delegate;
        private int tokenCount;
        private int depth;
        private int maximumDepth;

        private BoundedTokenSource(TokenSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Token nextToken() {
            Token token = this.delegate.nextToken();
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                this.tokenCount++;
                switch (token.getType()) {
                    case EmbeddedLanguageLexer.LBRACE, EmbeddedLanguageLexer.LBRACKET, EmbeddedLanguageLexer.LPAREN -> {
                        this.depth++;
                        this.maximumDepth = Math.max(this.maximumDepth, this.depth);
                    }
                    case
                        EmbeddedLanguageLexer.RBRACE,
                        EmbeddedLanguageLexer.RBRACKET,
                        EmbeddedLanguageLexer.RPAREN -> this.depth--;
                    default -> {
                    }
                }
            }
            return token;
        }

        int tokenCount() {
            return this.tokenCount;
        }

        int maximumDepth() {
            return this.maximumDepth;
        }

        @Override
        public int getLine() {
            return this.delegate.getLine();
        }

        @Override
        public int getCharPositionInLine() {
            return this.delegate.getCharPositionInLine();
        }

        @Override
        public CharStream getInputStream() {
            return this.delegate.getInputStream();
        }

        @Override
        public String getSourceName() {
            return this.delegate.getSourceName();
        }

        @Override
        public void setTokenFactory(TokenFactory<?> factory) {
            this.delegate.setTokenFactory(factory);
        }

        @Override
        public TokenFactory<?> getTokenFactory() {
            return this.delegate.getTokenFactory();
        }
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
