package org.example.minic.mips;

import org.example.minic.ir.*;

import java.util.*;

/**
 * Traduce TAC -> MIPS32 (PCSpim).
 * - Frame fijo de 40 bytes.
 * - Un slot por cada nombre/temporal que aparezca en TAC.
 * - Soporta: MOV, ADD,SUB,MUL,DIV,MOD, LT,LE,GT,GE,EQ,NEQ, AND,OR, NOT,
 *            LABEL, IFZ, GOTO, PARAM, CALL (built-ins), RET.
 * - Built-ins: printInt(x), printChar(c), printString("...").
 */
public class MipsGen {

    private final StringBuilder data = new StringBuilder();
    private final StringBuilder text = new StringBuilder();
    private final Map<String, Integer> slot = new HashMap<>();
    private final List<String> paramQueue = new ArrayList<>();
    private int nextSlot = -4;

    private int strCount = 0;

    // -------- API principal --------
    public String emitProgram(TacProgram p) {
        data.setLength(0);
        text.setLength(0);

        data.append(".data\n");
        text.append(".text\n");
        text.append(".globl __start\n");
        text.append("__start:\n");
        text.append("  jal  main\n");
        text.append("  li   $v0, 10\n");
        text.append("  syscall\n\n");

        for (TacFunction f : p.functions) {
            emitFunction(f);
        }

        return new StringBuilder().append(data).append(text).toString();
    }

    // -------- Función --------
    private void emitFunction(TacFunction f) {
        slot.clear();
        paramQueue.clear();
        nextSlot = -4;

        if ("main".equals(f.name)) {
            text.append(".globl main\n");
        }
        text.append(f.name).append(":\n");

        emitPrologue();

        for (int i = 0; i < f.params.size() && i < 4; i++) {
            String pName = f.params.get(i);
            switch (i) {
                case 0 -> emitStore("$a0", pName);
                case 1 -> emitStore("$a1", pName);
                case 2 -> emitStore("$a2", pName);
                case 3 -> emitStore("$a3", pName);
            }
        }

        boolean sawRet = false;  // evitar epílogo duplicado

        for (TacInstr i : f.code) {
            switch (i.op) {
                case MOV -> emitMov(i);
                case ADD, SUB, MUL, DIV, MOD -> emitBinArith(i);
                case LT, LE, GT, GE, EQ, NEQ -> emitCmp(i);
                case AND, OR -> emitLogic(i);
                case NOT -> emitNot(i);

                case LABEL -> text.append(i.a).append(":\n");
                case IFZ -> {
                    emitLoad(i.a, "$t0");
                    text.append("beq $t0, $zero, ").append(i.b).append("\n");
                }
                case GOTO -> text.append("j ").append(i.a).append("\n");

                case PARAM -> paramQueue.add(i.a);
                case CALL -> emitCall(i);
                case RET -> {
                    emitRet(i);
                    sawRet = true;
                }
            }
        }

        // Si no hubo RET explícito, retornar 0 con epílogo una sola vez
        if (!sawRet) {
            emitReturn0();
        }
    }

    // -------- Frame --------
    private void emitPrologue() {
        text.append("addiu $sp, $sp, -40\n");
        text.append("sw $ra, 36($sp)\n");
        text.append("sw $fp, 32($sp)\n");
        text.append("move $fp, $sp\n");
    }

    private void emitEpilogue() {
        text.append("move $sp, $fp\n");
        text.append("lw   $fp, 32($sp)\n");
        text.append("lw   $ra, 36($sp)\n");
        text.append("addiu $sp, $sp, 40\n");
        text.append("jr   $ra\n");
    }

    private void emitReturn0() {
        text.append("li   $v0, 0\n");
        emitEpilogue();
        text.append("\n");
    }

    // -------- Slots / Loads / Stores --------
    private int slotOf(String name) {
        if (name == null) throw new IllegalArgumentException("nombre null");
        return slot.computeIfAbsent(name, k -> {
            int s = nextSlot;
            nextSlot -= 4;
            return s;
        });
    }

    private static boolean isInt(String s) {
        return s != null && s.matches("-?\\d+");
    }

    private static boolean isStringLit(String s) {
        return s != null && s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"';
    }

    private static boolean isCharLit(String s) {
        return s != null && s.length() >= 3 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'';
    }

    // Convierte un literal char ('a', '\n', '\t', '\\', '\'', '\"', '\r') a su código ASCII
    private static int charCode(String lit) {
        if (!isCharLit(lit)) throw new IllegalArgumentException("No es char literal: " + lit);
        String body = lit.substring(1, lit.length() - 1); // sin comillas simples
        if (body.length() == 1) {
            return (int) body.charAt(0);
        }
        if (body.length() == 2 && body.charAt(0) == '\\') {
            char e = body.charAt(1);
            return switch (e) {
                case 'n' -> 10;
                case 't' -> 9;
                case 'r' -> 13;
                case '\\' -> 92;
                case '\'' -> 39;
                case '\"' -> 34;
                case '0' -> 0;
                default -> e;
            };
        }
        return (int) body.charAt(0);
    }

    private void emitLoad(String src, String reg) {
        if (isInt(src)) {
            text.append("li   ").append(reg).append(", ").append(src).append("\n");
        } else if (isCharLit(src)) {                       // <--- NUEVO: soportar 'a', '\n', etc.
            text.append("li   ").append(reg).append(", ").append(charCode(src)).append("\n");
        } else {
            int off = slotOf(src);
            text.append("lw   ").append(reg).append(", ").append(off).append("($fp)\n");
        }
    }

    private void emitStore(String reg, String dst) {
        int off = slotOf(dst);
        text.append("sw   ").append(reg).append(", ").append(off).append("($fp)\n");
    }

    // -------- Instrucciones TAC --------
    private void emitMov(TacInstr i) {
        if (i.r == null) return;
        // r = a
        if (isInt(i.a)) {
            text.append("li   $t0, ").append(i.a).append("\n");
        } else if (isStringLit(i.a)) {                     // <--- NUEVO: mover strings (dirección)
            String label = newStringLabel(i.a);
            text.append("la   $t0, ").append(label).append("\n");
        } else if (isCharLit(i.a)) {                       // <--- NUEVO: mover char literal
            text.append("li   $t0, ").append(charCode(i.a)).append("\n");
        } else {
            emitLoad(i.a, "$t0");
        }
        emitStore("$t0", i.r);
    }

    private void emitBinArith(TacInstr i) {
        emitLoad(i.a, "$t0");
        emitLoad(i.b, "$t1");

        switch (i.op) {
            case ADD -> text.append("addu $t2, $t0, $t1\n");
            case SUB -> text.append("subu $t2, $t0, $t1\n");
            case MUL -> text.append("mul  $t2, $t0, $t1\n"); // pseudo de SPIM
            case DIV -> {
                text.append("div  $t0, $t1\n");
                text.append("mflo $t2\n");
            }
            case MOD -> {
                text.append("div  $t0, $t1\n");
                text.append("mfhi $t2\n");
            }
        }
        emitStore("$t2", i.r);
    }

    private void emitCmp(TacInstr i) {
        emitLoad(i.a, "$t0");
        emitLoad(i.b, "$t1");

        switch (i.op) {
            case LT -> text.append("slt  $t2, $t0, $t1\n");
            case LE -> {
                text.append("slt  $t2, $t1, $t0\n");
                text.append("xori $t2, $t2, 1\n");
            }
            case GT -> text.append("slt  $t2, $t1, $t0\n");
            case GE -> {
                text.append("slt  $t2, $t0, $t1\n");
                text.append("xori $t2, $t2, 1\n");
            }
            case EQ -> text.append("seq  $t2, $t0, $t1\n");  // pseudo
            case NEQ -> text.append("sne  $t2, $t0, $t1\n"); // pseudo
        }
        emitStore("$t2", i.r);
    }

    private void emitLogic(TacInstr i) {
        emitLoad(i.a, "$t0");
        emitLoad(i.b, "$t1");
        text.append("sne  $t0, $t0, $zero\n");
        text.append("sne  $t1, $t1, $zero\n");
        if (i.op == TacOp.AND) {
            text.append("and  $t2, $t0, $t1\n");
        } else {
            text.append("or   $t2, $t0, $t1\n");
        }
        emitStore("$t2", i.r);
    }

    private void emitNot(TacInstr i) {
        emitLoad(i.a, "$t0");
        text.append("sne  $t0, $t0, $zero\n");
        text.append("xori $t2, $t0, 1\n");
        emitStore("$t2", i.r);
    }

    // -------- Llamadas --------
    private void emitCall(TacInstr i) {
        String fname = i.a;
        int n = 0;
        try { n = (i.b == null) ? 0 : Integer.parseInt(i.b); } catch (Exception ignored) {}

        List<String> args = new ArrayList<>();
        for (int k = paramQueue.size() - n; k < paramQueue.size(); k++) {
            if (k >= 0 && k < paramQueue.size()) args.add(paramQueue.get(k));
        }
        if (n > 0) {
            int newSize = Math.max(0, paramQueue.size() - n);
            while (paramQueue.size() > newSize) paramQueue.remove(paramQueue.size() - 1);
        }

        switch (fname) {
            case "printInt" -> {
                emitArgToA0(args, 0);
                text.append("li $v0, 1\nsyscall\n");
            }
            case "printChar" -> {
                emitArgToA0(args, 0);
                text.append("li $v0, 11\nsyscall\n");
            }
            case "printString" -> {
                if (!args.isEmpty() && isStringLit(args.get(0))) {
                    String label = newStringLabel(args.get(0));
                    text.append("la   $a0, ").append(label).append("\n");
                } else {
                    emitArgToA0(args, 0);
                }
                text.append("li $v0, 4\nsyscall\n");
            }
            default -> {
                // Cargar hasta 4 args en $a0..$a3
                for (int k = 0; k < args.size() && k < 4; k++) {
                    emitArgToAi(args, k);
                }
                text.append("jal ").append(fname).append("\n");
                if (i.r != null) emitStore("$v0", i.r);
            }
        }
    }

    private void emitArgToA0(List<String> args, int idx) {
        if (idx < 0 || idx >= args.size()) {
            text.append("li   $a0, 0\n");
            return;
        }
        String a = args.get(idx);
        if (isInt(a)) {
            text.append("li   $a0, ").append(a).append("\n");
        } else if (isStringLit(a)) {
            String label = newStringLabel(a);
            text.append("la   $a0, ").append(label).append("\n");
        } else if (isCharLit(a)) {               // mantiene el salto de línea correcto
            text.append("li   $a0, ").append(charCode(a)).append("\n");
        } else {
            emitLoad(a, "$a0");
        }
    }

    private void emitArgToAi(List<String> args, int idx) {
        if (idx < 0 || idx >= args.size()) return;
        String a = args.get(idx);
        String reg = switch (idx) {
            case 0 -> "$a0";
            case 1 -> "$a1";
            case 2 -> "$a2";
            default -> "$a3";
        };

        if (isInt(a)) {
            text.append("li   ").append(reg).append(", ").append(a).append("\n");
        } else if (isStringLit(a)) {
            String label = newStringLabel(a);
            text.append("la   ").append(reg).append(", ").append(label).append("\n");
        } else if (isCharLit(a)) {
            text.append("li   ").append(reg).append(", ").append(charCode(a)).append("\n");
        } else {
            emitLoad(a, reg);
        }
    }


    private String newStringLabel(String literal) {
        String label = "str_" + (strCount++);
        String body = literal.substring(1, literal.length() - 1); // sin comillas
        data.append(label).append(": .asciiz \"").append(body).append("\"\n");
        return label;
    }

    private void emitRet(TacInstr i) {
        if (i.a != null) {
            if (isInt(i.a)) {
                text.append("li   $v0, ").append(i.a).append("\n");
            } else if (isCharLit(i.a)) {
                text.append("li   $v0, ").append(charCode(i.a)).append("\n");
            } else {
                emitLoad(i.a, "$v0");
            }
        } else {
            text.append("li   $v0, 0\n");
        }
        emitEpilogue();
        text.append("\n");
    }
}
