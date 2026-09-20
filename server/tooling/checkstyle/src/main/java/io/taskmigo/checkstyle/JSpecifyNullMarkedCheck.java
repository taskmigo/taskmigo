package io.taskmigo.checkstyle;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.AnnotationUtil;
import com.puppycrawl.tools.checkstyle.utils.CheckUtil;
import java.util.Optional;

/// Requires existing package-info.java files to opt into JSpecify null-marked semantics.
///
/// Package presence is deliberately handled by Checkstyle's built-in JavadocPackage check.
public final class JSpecifyNullMarkedCheck extends AbstractCheck {

    static final String MSG_MISSING_NULL_MARKED = "jspecify.nullMarked";

    private static final String NULL_MARKED = "NullMarked";
    private static final String JSPECIFY_NULL_MARKED = "org.jspecify.annotations.NullMarked";

    private boolean hasJSpecifyNullMarkedImport;
    private Optional<DetailAST> packageDefinition = Optional.empty();

    @Override
    public int[] getDefaultTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] { TokenTypes.PACKAGE_DEF, TokenTypes.IMPORT };
    }

    @Override
    public int[] getAcceptableTokens() {
        return getRequiredTokens();
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        hasJSpecifyNullMarkedImport = false;
        packageDefinition = Optional.empty();
    }

    @Override
    public void visitToken(DetailAST ast) {
        if (!CheckUtil.isPackageInfo(getFilePath())) {
            return;
        }

        if (ast.getType() == TokenTypes.PACKAGE_DEF) {
            packageDefinition = Optional.of(ast);
        } else if (
            ast.getType() == TokenTypes.IMPORT &&
            JSPECIFY_NULL_MARKED.equals(FullIdent.createFullIdentBelow(ast).getText())
        ) {
            hasJSpecifyNullMarkedImport = true;
        }
    }

    @Override
    public void finishTree(DetailAST rootAST) {
        if (CheckUtil.isPackageInfo(getFilePath()) && packageDefinition.isPresent()) {
            DetailAST packageDef = packageDefinition.orElseThrow();
            boolean hasNullMarked = AnnotationUtil.containsAnnotation(packageDef, NULL_MARKED);
            if (!hasNullMarked || !hasJSpecifyNullMarkedImport) {
                log(packageDef, MSG_MISSING_NULL_MARKED);
            }
        }
    }
}
