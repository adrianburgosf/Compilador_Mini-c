grammar MiniC;

@header { package org.example.minic.parser; }

// ---------------- Parser ----------------
program         : (functionDecl | varDecl)* EOF ;

// tipos
type            : INT | VOID | CHAR | BOOL | STRING ;

functionDecl    : type ID LPAREN paramList? RPAREN block ;
paramList       : param (COMMA param)* ;
param           : type ID ;

block           : LBRACE stmt* RBRACE ;

stmt            : returnStmt
                | exprStmt
                | varDecl
                | selectionStmt          // <-- if / else
                | iterationStmt          // <-- while
                | block
                ;

selectionStmt   : IF LPAREN expr RPAREN stmt (ELSE stmt)? ;
iterationStmt   : WHILE LPAREN expr RPAREN stmt ;

returnStmt      : RETURN expr? SEMI ;
exprStmt        : expr? SEMI ;

// precedencias
expr            : assignment ;
assignment      : logicalOr (ASSIGN assignment)? ;
logicalOr       : logicalAnd (OR logicalAnd)* ;
logicalAnd      : equality (AND equality)* ;
equality        : relational ((EQ | NEQ) relational)* ;
relational      : additive ((LT | LE | GT | GE) additive)* ;
additive        : multiplicative ((PLUS | MINUS) multiplicative)* ;
multiplicative  : unary ((STAR | DIV | MOD) unary)* ;
unary           : (NOT | MINUS) unary
                | primary
                ;

primary         : INT_LIT
                | CHAR_LIT
                | STR_LIT
                | TRUE
                | FALSE
                | ID
                | ID LPAREN argList? RPAREN
                | LPAREN expr RPAREN
                ;

argList         : expr (COMMA expr)* ;

varDecl         : type initDeclarator (COMMA initDeclarator)* SEMI ;
initDeclarator  : ID (ASSIGN expr)? ;

// ---------------- Lexer ----------------
WS              : [ \t\r\n]+ -> skip ;
COMMENT         : '/*' .*? '*/' -> skip ;
LINE_COMMENT    : '//' ~[\r\n]* -> skip ;

// keywords
INT             : 'int' ;
VOID            : 'void' ;
CHAR            : 'char' ;
BOOL            : 'bool' ;
STRING          : 'string' ;
RETURN          : 'return' ;
TRUE            : 'true' ;
FALSE           : 'false' ;
IF              : 'if' ;               // <-- nuevo
ELSE            : 'else' ;             // <-- nuevo
WHILE           : 'while' ;            // <-- nuevo

// id y literales
ID              : [a-zA-Z_][a-zA-Z_0-9]* ;
INT_LIT         : [0-9]+ ;
CHAR_LIT        : '\'' ( ~['\\\r\n] | '\\' ['"\\nrt0] ) '\'' ;
STR_LIT         : '"' ( ~["\\\r\n] | '\\' ['"\\nrt0] )* '"' ;

// ops y símbolos
PLUS  : '+' ;
MINUS : '-' ;
STAR  : '*' ;
DIV   : '/' ;
MOD   : '%' ;

ASSIGN: '=' ;
EQ    : '==' ;
NEQ   : '!=' ;
LT    : '<' ;
LE    : '<=' ;
GT    : '>' ;
GE    : '>=' ;

AND   : '&&' ;
OR    : '||' ;
NOT   : '!' ;

LPAREN: '(' ;
RPAREN: ')' ;
LBRACE: '{' ;
RBRACE: '}' ;
SEMI  : ';' ;
COMMA : ',' ;
