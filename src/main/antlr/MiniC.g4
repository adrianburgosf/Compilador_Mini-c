grammar MiniC;

@header { package org.example.minic.parser; }

// ---------------- Parser ----------------
program         : (functionDecl | varDecl)* EOF ;

// tipos por ahora
type            : INT | VOID | CHAR | BOOL | STRING ;

functionDecl    : type ID LPAREN paramList? RPAREN block ;
paramList       : param (COMMA param)* ;
param           : type ID ;

block           : LBRACE stmt* RBRACE ;

stmt            : returnStmt
                | exprStmt
                | varDecl
                | block
                ;

returnStmt      : RETURN expr? SEMI ;
exprStmt        : expr? SEMI ;

// precedencias (de menor a mayor)
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
                | LPAREN expr RPAREN
                ;

// var decl con lista e inicialización opcional
varDecl         : type initDeclarator (COMMA initDeclarator)* SEMI ;
initDeclarator  : ID (ASSIGN expr)? ;

// ---------------- Lexer ----------------
// ¡Orden importa!: keywords antes de ID

WS              : [ \t\r\n]+ -> skip ;
COMMENT         : '/*' .*? '*/' -> skip ;
LINE_COMMENT    : '//' ~[\r\n]* -> skip ;

// palabras reservadas
INT             : 'int' ;
VOID            : 'void' ;
CHAR            : 'char' ;
BOOL            : 'bool' ;
STRING          : 'string' ;
RETURN          : 'return' ;
TRUE            : 'true' ;
FALSE           : 'false' ;

// identificadores y literales
ID              : [a-zA-Z_][a-zA-Z_0-9]* ;
INT_LIT         : [0-9]+ ;

// literal de caracter: 'a', '\n', '\\', '\''
CHAR_LIT        : '\'' ( ~['\\\r\n] | '\\' ['"\\nrt0] ) '\'' ;

// literal de cadena con escapes comunes
STR_LIT         : '"' ( ~["\\\r\n] | '\\' ['"\\nrt0] )* '"' ;

// operadores y símbolos
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
