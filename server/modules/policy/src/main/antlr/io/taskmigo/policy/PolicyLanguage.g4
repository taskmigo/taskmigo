grammar PolicyLanguage;

@header { package io.taskmigo.policy.antlr; }

policy: statement* EOF;
statement: constDecl | ifStatement | returnStatement;
block: LBRACE statement* RBRACE;
constDecl: CONST IDENT ASSIGN expression SEMICOLON;
returnStatement: RETURN expression SEMICOLON;
ifStatement: IF LPAREN expression RPAREN block (ELSE (ifStatement | block))?;
expression: orExpression;
orExpression: andExpression (OR andExpression)*;
andExpression: equalityExpression (AND equalityExpression)*;
equalityExpression: comparisonExpression ((EQUAL | NOT_EQUAL) comparisonExpression)*;
comparisonExpression: membershipExpression ((LESS | LESS_EQUAL | GREATER | GREATER_EQUAL) membershipExpression)*;
membershipExpression: additiveExpression (IN additiveExpression)?;
additiveExpression: multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*;
multiplicativeExpression: unaryExpression ((STAR | SLASH | PERCENT) unaryExpression)*;
unaryExpression: (NOT | PLUS | MINUS) unaryExpression | primary;
primary: literal | listLiteral | reference | LPAREN expression RPAREN;
reference: IDENT (DOT IDENT)*;
listLiteral: LBRACKET (expression (COMMA expression)*)? RBRACKET;
literal: TRUE | FALSE | NULL | NUMBER | STRING;

CONST: 'const'; IF: 'if'; ELSE: 'else'; RETURN: 'return'; IN: 'in'; TRUE: 'true'; FALSE: 'false'; NULL: 'null';
EQUAL: '=='; NOT_EQUAL: '!='; LESS_EQUAL: '<='; GREATER_EQUAL: '>='; AND: '&&'; OR: '||'; LESS: '<'; GREATER: '>';
ASSIGN: '='; PLUS: '+'; MINUS: '-'; STAR: '*'; SLASH: '/'; PERCENT: '%'; NOT: '!'; LPAREN: '('; RPAREN: ')';
LBRACE: '{'; RBRACE: '}'; LBRACKET: '['; RBRACKET: ']'; DOT: '.'; COMMA: ','; SEMICOLON: ';';
NUMBER: [0-9]+ ('.' [0-9]+)? ([eE] [+-]? [0-9]+)?;
STRING: '"' (ESCAPE | ~["\\\r\n])* '"';
IDENT: [a-zA-Z_] [a-zA-Z0-9_]*;
fragment ESCAPE: '\\' (["\\/bfnrt] | 'u' HEX HEX HEX HEX);
fragment HEX: [0-9a-fA-F];
LINE_COMMENT: '//' ~[\r\n]* -> channel(HIDDEN);
BLOCK_COMMENT: '/*' .*? '*/' -> channel(HIDDEN);
WS: [ \t\r\n]+ -> channel(HIDDEN);
