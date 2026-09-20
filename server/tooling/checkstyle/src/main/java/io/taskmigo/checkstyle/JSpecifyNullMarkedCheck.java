package io.taskmigo.checkstyle;

import com.puppycrawl.tools.checkstyle.StatelessCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.utils.AnnotationUtil;
import com.puppycrawl.tools.checkstyle.utils.CheckUtil;

/// Requires existing package-info.java files to opt into JSpecify null-marked semantics.
///
/// Package presence is deliberately handled by Checkstyle's built-in JavadocPackage check.
@StatelessCheck
public final class JSpecifyNullMarkedCheck extends AbstractCheck {

    static final String MSG_MISSING_NULL_MARKED = "jspecify.nullMarked";

    private static final String NULL_MARKED = "NullMarked";
    private static final String JSPECIFY_NULL_MARKED = "org.jspecify.annotations.NullMarked";
    private static final String JSPECIFY_ANNOTATIONS_WILDCARD = "org.jspecify.annotations.*";

    @Override
    public int[] getDefaultTokens() {
        return new int[] { TokenTypes.COMPILATION_UNIT };
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] { TokenTypes.COMPILATION_UNIT };
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] { TokenTypes.COMPILATION_UNIT };
    }

    @Override
    public void visitToken(DetailAST compilationUnit) {
        if (!CheckUtil.isPackageInfo(this.getFilePath())) {
            return;
        }

        DetailAST packageDefinition = compilationUnit.findFirstToken(TokenTypes.PACKAGE_DEF);
        if (packageDefinition != null && !isJSpecifyNullMarked(compilationUnit, packageDefinition)) {
            this.log(packageDefinition, MSG_MISSING_NULL_MARKED);
        }
    }

    private static boolean isJSpecifyNullMarked(DetailAST compilationUnit, DetailAST packageDefinition) {
        if (AnnotationUtil.containsAnnotation(packageDefinition, JSPECIFY_NULL_MARKED)) {
            return true;
        }

        return (
            AnnotationUtil.containsAnnotation(packageDefinition, NULL_MARKED) &&
            hasJSpecifyNullMarkedImport(compilationUnit)
        );
    }

    private static boolean hasJSpecifyNullMarkedImport(DetailAST compilationUnit) {
        boolean hasJSpecifyExplicitImport = false;
        boolean hasNonJSpecifyExplicitImport = false;
        boolean hasJSpecifyWildcardImport = false;

        for (DetailAST child = compilationUnit.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getType() == TokenTypes.IMPORT) {
                String importedType = FullIdent.createFullIdentBelow(child).getText();
                if (JSPECIFY_NULL_MARKED.equals(importedType)) {
                    hasJSpecifyExplicitImport = true;
                } else if (importedType.endsWith("." + NULL_MARKED)) {
                    hasNonJSpecifyExplicitImport = true;
                } else if (JSPECIFY_ANNOTATIONS_WILDCARD.equals(importedType)) {
                    hasJSpecifyWildcardImport = true;
                }
            }
        }

        return (
            !hasNonJSpecifyExplicitImport &&
            (hasJSpecifyExplicitImport || hasJSpecifyWildcardImport)
        );
    }
}
