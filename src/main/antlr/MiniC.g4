grammar MiniC;

@header { package org.example.minic.parser; }

// ---------- Parser ----------
program         : (functionDecl | varDecl)* EOF ;

type            : INT | VOID ;

functionDecl    : type ID LPAREN paramList? RPAREN block ;
paramList       : param (COMMA param)* ;
param           : type ID ;

block           : LBRACE stmt* RBRACE ;

stmt            : returnStmt
                | exprStmt
                | varDecl               // <- ahora también deja declarar dentro de bloques
                | block
                ;

returnStmt      : RETURN expr? SEMI ;
exprStmt        : expr? SEMI ;

// Precedencias (de menor a mayor)
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
                | ID
                | LPAREN expr RPAREN
                ;

// ---------------- NEW: varDecl con inicialización y lista ----------------
varDecl         : type initDeclarator (COMMA initDeclarator)* SEMI ;
initDeclarator  : ID (ASSIGN expr)? ;

// ---------- Lexer ----------
WS              : [ \t\r\n]+ -> skip ;
COMMENT         : '/*' .*? '*/' -> skip ;
LINE_COMMENT    : '//' ~[\r\n]* -> skip ;

// Palabras reservadas
INT             : 'int' ;
VOID            : 'void' ;
RETURN          : 'return' ;

// Identificadores y literales
ID              : [a-zA-Z_][a-zA-Z_0-9]* ;
INT_LIT         : [0-9]+ ;

// Operadores y símbolos
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
