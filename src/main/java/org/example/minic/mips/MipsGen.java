package org.example.minic.mips;

import org.example.minic.ir.*;

import java.util.*;

/**
 * TAC -> MIPS32 (PCSpim).
 *
 * Fixes:
 * 1) Slots usan offsets negativos respecto a $fp (fp-4, fp-8,...).
 *    Entonces $fp debe apuntar al "tope" del frame (el $sp viejo),
 *    no al $sp ya bajado. Si no, escribes fuera del frame y todo da 0.
 * 2) Evita labels null: (PCSpim warning "Label defined second time ... null:")
 */
public class MipsGen {

    private final StringBuilder data = new StringBuilder();
    private final StringBuilder text = new StringBuilder();

    // stack slots: name -> offset (negativo) relativo a $fp
    private final Map<String, Integer> slot = new HashMap<>();
    private final List<String> paramQueue = new ArrayList<>();
    private int nextSlot = -4;

    // frame size actual (por funcion)
    private int frameBytes = 0;

    // strings: dedup por literal exacto
    private final Map<String, String> stringPool = new HashMap<>();
    private int strCount = 0;

    // label null defense
    private static final String NULL_LABEL = "__L_null";
    private boolean nullLabelEmitted = false;

    // -------- API principal --------
    public String emitProgram(TacProgram p) {
        data.setLength(0);
        text.setLength(0);
        stringPool.clear();
        strCount = 0;

        data.append(".data\n");

        // Globales (arreglos/vars globales) -> .data
        for (TacGlobal g : p.globals) {
            data.append(".align 2\n");
            data.append(g.name).append(":\n");
            data.append("  .space ").append(g.bytes).append("\n");
        }

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

    // -------- Funcion --------
    private void emitFunction(TacFunction f) {
        slot.clear();
        paramQueue.clear();
        nextSlot = -4;
        nullLabelEmitted = false;

        // Pre-scan: fija slots y calcula frameBytes antes del prologo
        preAllocateSlots(f);
        // 8 bytes para guardar $ra y $fp en el fondo del frame
        int localsBytes = slot.size() * 4;
        frameBytes = align16(localsBytes + 8);
        if (frameBytes < 16) frameBytes = 16;

        if ("main".equals(f.name)) {
            text.append(".globl main\n");
        }
        text.append(f.name).append(":\n");

        emitPrologue();

        // Guardar params ($a0..$a3) en slots
        for (int i = 0; i < f.params.size() && i < 4; i++) {
            String pName = f.params.get(i);
            switch (i) {
                case 0 -> emitStore("$a0", pName);
                case 1 -> emitStore("$a1", pName);
                case 2 -> emitStore("$a2", pName);
                case 3 -> emitStore("$a3", pName);
            }
        }

        boolean sawRet = false;

        for (TacInstr i : f.code) {
            switch (i.op) {
                case MOV -> emitMov(i);
                case ADD, SUB, MUL, DIV, MOD -> emitBinArith(i);
                case LT, LE, GT, GE, EQ, NEQ -> emitCmp(i);
                case AND, OR -> emitLogic(i);
                case NOT -> emitNot(i);

                case LOAD -> emitLoadMem(i);
                case STORE -> emitStoreMem(i);

                case LABEL -> emitLabel(i.a);

                case IFZ -> {
                    emitLoad(i.a, "$t0");
                    text.append("beq $t0, $zero, ").append(canonLabel(i.b)).append("\n");
                }
                case GOTO -> text.append("j ").append(canonLabel(i.a)).append("\n");

                case PARAM -> paramQueue.add(i.a);
                case CALL -> emitCall(i);

                case RET -> {
                    emitRet(i);
                    sawRet = true;
                }
            }
        }

        if (!sawRet) {
            emitReturn0();
        }
    }

    // -------- Frame --------
    private void emitPrologue() {
        // Reservar frame
        text.append("addiu $sp, $sp, -").append(frameBytes).append("\n");
        // Guardar $ra y $fp al fondo del frame
        text.append("sw   $ra, 0($sp)\n");
        text.append("sw   $fp, 4($sp)\n");
        // $fp apunta al tope del frame (sp viejo): fp = sp + frameBytes
        text.append("addiu $fp, $sp, ").append(frameBytes).append("\n");
    }

    private void emitEpilogue() {
        // Restaurar regs desde el fondo del frame (sp actual)
        text.append("lw   $ra, 0($sp)\n");
        text.append("lw   $fp, 4($sp)\n");
        text.append("addiu $sp, $sp, ").append(frameBytes).append("\n");
        text.append("jr   $ra\n");
    }

    private void emitReturn0() {
        text.append("li   $v0, 0\n");
        emitEpilogue();
        text.append("\n");
    }

    // -------- Slots / Loads / Stores --------
    private int slotOf(String name) {
        if (name == null) throw new IllegalArgumentException("slot name null");
        return slot.computeIfAbsent(name, k -> {
            int s = nextSlot;   // -4, -8, -12, ...
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

    // Convierte char literal a ASCII (soporta escapes comunes)
    private static int charCode(String lit) {
        if (!isCharLit(lit)) throw new IllegalArgumentException("Not char literal: " + lit);
        String body = lit.substring(1, lit.length() - 1);
        if (body.length() == 1) return (int) body.charAt(0);

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
        } else if (isCharLit(src)) {
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

    // -------- TAC -> MIPS --------
    private void emitMov(TacInstr i) {
        if (i.r == null) return;

        if (isInt(i.a)) {
            text.append("li   $t0, ").append(i.a).append("\n");
        } else if (isStringLit(i.a)) {
            String label = stringLabel(i.a);
            text.append("la   $t0, ").append(label).append("\n");
        } else if (isCharLit(i.a)) {
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
            case MUL -> text.append("mul  $t2, $t0, $t1\n"); // SPIM pseudo
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
            case EQ -> text.append("seq  $t2, $t0, $t1\n");
            case NEQ -> text.append("sne  $t2, $t0, $t1\n");
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

    // -------- Memoria (global arrays) --------
    // LOAD: r = load baseLabel, offsetBytes
    private void emitLoadMem(TacInstr i) {
        text.append("la   $t1, ").append(i.a).append("\n");
        emitLoad(i.b, "$t2"); // offset in bytes
        text.append("addu $t1, $t1, $t2\n");
        text.append("lw   $t0, 0($t1)\n");
        emitStore("$t0", i.r);
    }

    // STORE: store value -> baseLabel[offsetBytes]
    // a=value, b=baseLabel, r=offsetBytes
    private void emitStoreMem(TacInstr i) {
        emitLoad(i.a, "$t0");
        text.append("la   $t1, ").append(i.b).append("\n");
        emitLoad(i.r, "$t2");
        text.append("addu $t1, $t1, $t2\n");
        text.append("sw   $t0, 0($t1)\n");
    }

    // -------- Calls --------
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
            case "printInt", "print_int" -> {
                emitArgToA0(args, 0);
                text.append("li $v0, 1\nsyscall\n");
            }
            case "printChar", "print_char" -> {
                emitArgToA0(args, 0);
                text.append("li $v0, 11\nsyscall\n");
            }
            case "printString", "print_str" -> {
                if (!args.isEmpty() && isStringLit(args.get(0))) {
                    String label = stringLabel(args.get(0));
                    text.append("la   $a0, ").append(label).append("\n");
                } else {
                    emitArgToA0(args, 0);
                }
                text.append("li $v0, 4\nsyscall\n");
            }
            case "println", "nl" -> {
                text.append("li $a0, 10\n");
                text.append("li $v0, 11\nsyscall\n");
            }
            default -> {
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
            String label = stringLabel(a);
            text.append("la   $a0, ").append(label).append("\n");
        } else if (isCharLit(a)) {
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
            String label = stringLabel(a);
            text.append("la   ").append(reg).append(", ").append(label).append("\n");
        } else if (isCharLit(a)) {
            text.append("li   ").append(reg).append(", ").append(charCode(a)).append("\n");
        } else {
            emitLoad(a, reg);
        }
    }

    // -------- Return --------
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

    // -------- Labels (defense for null) --------
    private void emitLabel(String lbl) {
        String L = canonLabel(lbl);
        if (lbl == null) {
            if (nullLabelEmitted) {
                // avoid "Label defined for second time ... null:"
                text.append("# (skip duplicate null label)\n");
                return;
            }
            nullLabelEmitted = true;
        }
        text.append(L).append(":\n");
    }

    private String canonLabel(String lbl) {
        return (lbl == null) ? NULL_LABEL : lbl;
    }

    // -------- Strings --------
    private String stringLabel(String literalWithQuotes) {
        return stringPool.computeIfAbsent(literalWithQuotes, lit -> {
            String label = "str_" + (strCount++);
            String body = lit.substring(1, lit.length() - 1); // remove quotes

            // Only protect against real quotes/newlines inside body (very defensive).
            body = escapeAsciizBody(body);

            data.append(label).append(": .asciiz \"").append(body).append("\"\n");
            return label;
        });
    }

    private static String escapeAsciizBody(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\"') out.append("\\\"");
            else if (c == '\n') out.append("\\n");
            else if (c == '\t') out.append("\\t");
            else if (c == '\r') out.append("\\r");
            else out.append(c);
        }
        return out.toString();
    }

    // -------- Pre-scan slots --------
    private void preAllocateSlots(TacFunction f) {
        LinkedHashSet<String> names = new LinkedHashSet<>();

        // params
        for (String p : f.params) addName(names, p);

        for (TacInstr i : f.code) {
            switch (i.op) {
                case LABEL, GOTO -> {
                    // labels are not stack vars
                }
                case IFZ -> {
                    addName(names, i.a);
                    // i.b is label
                }
                case CALL -> {
                    // i.a = function name, i.b = nargs
                    addName(names, i.r); // return temp/var
                }
                case PARAM -> addName(names, i.a);
                case RET -> addName(names, i.a);

                case LOAD -> {
                    // i.a is baseLabel (global), i.b offsetBytes, i.r dest
                    addName(names, i.b);
                    addName(names, i.r);
                }
                case STORE -> {
                    // i.a value, i.b baseLabel (global), i.r offsetBytes
                    addName(names, i.a);
                    addName(names, i.r);
                }
                default -> {
                    addName(names, i.a);
                    addName(names, i.b);
                    addName(names, i.r);
                }
            }
        }

        // allocate offsets deterministically
        for (String n : names) slotOf(n);
    }

    private void addName(Set<String> names, String s) {
        if (s == null) return;
        if (isInt(s) || isStringLit(s) || isCharLit(s)) return;
        names.add(s);
    }

    private static int align16(int n) {
        int r = n % 16;
        return (r == 0) ? n : (n + (16 - r));
    }
}
