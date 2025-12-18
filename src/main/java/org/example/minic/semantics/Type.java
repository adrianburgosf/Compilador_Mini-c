package org.example.minic.semantics;

public enum Type {
    INT, VOID, CHAR, BOOL, STRING;

    public static Type fromToken(String t) {
        return switch (t) {
            case "int" -> INT;
            case "void" -> VOID;
            case "char" -> CHAR;
            case "bool" -> BOOL;
            case "string" -> STRING;
            default -> INT;
        };
    }

    public static boolean isNumeric(Type t) { return t == INT || t == CHAR; }
    public static boolean isBooly(Type t) { return t == BOOL || t == INT || t == CHAR; }

    public static boolean assignmentCompatible(Type lhs, Type rhs) {
        if (lhs == rhs) return true;
        // Permitir int <- char y bool <- int/char
        if ((lhs == INT && rhs == CHAR) || (lhs == CHAR && rhs == INT)) return true;
        return false;
    }

    public static boolean comparable(Type a, Type b) {
        // ==/!= permitidos entre mismos tipos básicos y numéricos compatibles
        if (a == b) return true;
        if (isNumeric(a) && isNumeric(b)) return true;
        return false;
    }

    public static boolean compatibleReturn(Type fun, Type ret) {
        if (fun == VOID) return ret == VOID;
        return assignmentCompatible(fun, ret);
    }

    @Override public String toString() {
        return switch (this) {
            case INT -> "int";
            case VOID -> "void";
            case CHAR -> "char";
            case BOOL -> "bool";
            case STRING -> "string";
        };
    }
}
