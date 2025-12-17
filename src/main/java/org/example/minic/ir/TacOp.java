package org.example.minic.ir;

public enum TacOp {
    // movimiento y retorno
    MOV, RET,

    // aritmética
    ADD, SUB, MUL, DIV, MOD,

    // relacionales
    LT, LE, GT, GE, EQ, NEQ,

    // lógicos
    AND, OR, NOT,

    // llamadas
    PARAM, CALL,

    // control de flujo
    IFZ, GOTO, LABEL   // <-- NUEVO
}
