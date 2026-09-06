package io.taskmigo.auth.authorization.embeddedlanguage;

import io.taskmigo.embeddedlanguage.CompilerLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Exposes finite Embedded Language compiler limits through application configuration.
@ConfigurationProperties(prefix = "taskmigo.embedded-language.compiler")
public class EmbeddedLanguageCompilerProperties {

    private int maxSourceCharacters = 16_000;
    private int maxTokens = 4_096;
    private int maxSyntaxDepth = 40;
    private int maxIrNodes = 500;
    private int maxBlockDepth = 40;
    private int maxListElements = 100;

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

    /// Returns the configured IR-node limit.
    public int getMaxIrNodes() {
        return this.maxIrNodes;
    }

    /// Sets the configured IR-node limit.
    public void setMaxIrNodes(int value) {
        this.maxIrNodes = value;
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

    /// Converts application properties into the language module's immutable contract.
    public CompilerLimits limits() {
        return new CompilerLimits(
            this.maxSourceCharacters,
            this.maxTokens,
            this.maxSyntaxDepth,
            this.maxIrNodes,
            this.maxBlockDepth,
            this.maxListElements
        );
    }
}
