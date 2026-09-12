package io.taskmigo.authorization.embeddedlanguage;

import io.taskmigo.language.CompilerLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Exposes finite Embedded Language compiler limits through application configuration.
@ConfigurationProperties(prefix = "taskmigo.embedded-language.compiler")
public class EmbeddedLanguageCompilerProperties {

    private int maxSourceCharacters = 16_000;
    private int maxTokens = 4_096;
    private int maxSyntaxDepth = 40;
    private int maxSemanticAstNodes = 500;
    private int maxBlockDepth = 40;
    private int maxListElements = 100;
    private int maxQuantifierDepth = 20;
    private int maxLambdaDepth = 20;

    /// Returns the configured source-size limit.
    public int getMaxSourceCharacters() {
        return this.maxSourceCharacters;
    }

    /// Sets the configured source-size limit.
    public void setMaxSourceCharacters(int value) {
        this.maxSourceCharacters = value;
    }

    /// Returns the configured token limit.
    public int getMaxTokens() {
        return this.maxTokens;
    }

    /// Sets the configured token limit.
    public void setMaxTokens(int value) {
        this.maxTokens = value;
    }

    /// Returns the configured syntax-depth limit.
    public int getMaxSyntaxDepth() {
        return this.maxSyntaxDepth;
    }

    /// Sets the configured syntax-depth limit.
    public void setMaxSyntaxDepth(int value) {
        this.maxSyntaxDepth = value;
    }

    /// Returns the configured Semantic AST node limit.
    public int getMaxSemanticAstNodes() {
        return this.maxSemanticAstNodes;
    }

    /// Sets the configured Semantic AST node limit.
    public void setMaxSemanticAstNodes(int value) {
        this.maxSemanticAstNodes = value;
    }

    /// Returns the configured block-depth limit.
    public int getMaxBlockDepth() {
        return this.maxBlockDepth;
    }

    /// Sets the configured block-depth limit.
    public void setMaxBlockDepth(int value) {
        this.maxBlockDepth = value;
    }

    /// Returns the configured list-element limit.
    public int getMaxListElements() {
        return this.maxListElements;
    }

    /// Sets the configured list-element limit.
    public void setMaxListElements(int value) {
        this.maxListElements = value;
    }

    /// Returns the configured collection-quantifier nesting limit.
    public int getMaxQuantifierDepth() {
        return this.maxQuantifierDepth;
    }

    /// Sets the collection-quantifier nesting limit.
    public void setMaxQuantifierDepth(int value) {
        this.maxQuantifierDepth = value;
    }

    /// Returns the configured restricted-lambda nesting limit.
    public int getMaxLambdaDepth() {
        return this.maxLambdaDepth;
    }

    /// Sets the restricted-lambda nesting limit.
    public void setMaxLambdaDepth(int value) {
        this.maxLambdaDepth = value;
    }

    /// Converts application properties into the language module's immutable contract.
    public CompilerLimits limits() {
        return new CompilerLimits(
            this.maxSourceCharacters,
            this.maxTokens,
            this.maxSyntaxDepth,
            this.maxSemanticAstNodes,
            this.maxBlockDepth,
            this.maxListElements,
            this.maxQuantifierDepth,
            this.maxLambdaDepth
        );
    }
}
