package org.example.minic.ir;

public class TacOptimizer {

    private static boolean isIntLit(String s) {
        return s != null && s.matches("-?\\d+");
    }

    // Helpers por nombre (para tolerar variantes en el enum)
    private static boolean isAdd(TacOp op){ return op == TacOp.ADD || "ADD".equals(op.name()); }
    private static boolean isSub(TacOp op){ return op == TacOp.SUB || "SUB".equals(op.name()); }
    private static boolean isMul(TacOp op){ return op == TacOp.MUL || "MUL".equals(op.name()); }
    private static boolean isDiv(TacOp op){ return op == TacOp.DIV || "DIV".equals(op.name()); }
    private static boolean isMod(TacOp op){ return op == TacOp.MOD || "MOD".equals(op.name()); }

    private static boolean isLt(TacOp op){ return op == TacOp.LT  || "LT".equals(op.name()); }
    private static boolean isLe(TacOp op){ return op == TacOp.LE  || "LE".equals(op.name()); }
    private static boolean isGt(TacOp op){ return op == TacOp.GT  || "GT".equals(op.name()); }
    private static boolean isGe(TacOp op){ return op == TacOp.GE  || "GE".equals(op.name()); }
    private static boolean isEq(TacOp op){ return op == TacOp.EQ  || "EQ".equals(op.name()); }
    // Acepta NE | NEQ | NOTEQ
    private static boolean isNe(TacOp op){
        String n = op.name();
        return "NE".equals(n) || "NEQ".equals(n) || "NOTEQ".equals(n);
    }

    // Acepta AND|LAND y OR|LOR
    private static boolean isAnd(TacOp op){
        String n = op.name();
        return op == TacOp.AND || "AND".equals(n) || "LAND".equals(n);
    }
    private static boolean isOr(TacOp op){
        String n = op.name();
        return op == TacOp.OR || "OR".equals(n) || "LOR".equals(n);
    }

    private static boolean isBinFoldable(TacOp op){
        return isAdd(op)||isSub(op)||isMul(op)||isDiv(op)||isMod(op)
                ||isLt(op)||isLe(op)||isGt(op)||isGe(op)||isEq(op)||isNe(op)
                ||isAnd(op)||isOr(op);
    }

    private static Integer eval(TacOp op, int a, int b) {
        if (isAdd(op)) return a + b;
        if (isSub(op)) return a - b;
        if (isMul(op)) return a * b;
        if (isDiv(op)) return (b != 0) ? a / b : null;
        if (isMod(op)) return (b != 0) ? a % b : null;

        if (isLt(op))  return (a <  b) ? 1 : 0;
        if (isLe(op))  return (a <= b) ? 1 : 0;
        if (isGt(op))  return (a >  b) ? 1 : 0;
        if (isGe(op))  return (a >= b) ? 1 : 0;
        if (isEq(op))  return (a == b) ? 1 : 0;
        if (isNe(op))  return (a != b) ? 1 : 0;

        if (isAnd(op)) return ((a!=0) && (b!=0)) ? 1 : 0;
        if (isOr(op))  return ((a!=0) || (b!=0)) ? 1 : 0;

        return null;
    }

    public TacProgram optimize(TacProgram in) {
        TacProgram out = new TacProgram();

        for (TacFunction f : in.functions) {
            TacFunction g = new TacFunction(f.name);
            g.params.addAll(f.params);

            for (TacInstr i : f.code) {
                // Quitar mov redundante: mov x, x
                if (i.op == TacOp.MOV && i.r != null && i.a != null && i.r.equals(i.a)) {
                    continue;
                }

                // Folding de binarios con dos literales
                if (i.r != null && isBinFoldable(i.op) && isIntLit(i.a) && isIntLit(i.b)) {
                    Integer v = eval(i.op, Integer.parseInt(i.a), Integer.parseInt(i.b));
                    if (v != null) {
                        // Constructor: (TacOp op, String a, String b, String r)  => a=src, r=dst
                        g.emit(new TacInstr(TacOp.MOV, String.valueOf(v), null, i.r)); // r = const
                        continue;
                    }
                }

                // Default: copiar instrucción
                g.emit(i);
            }

            out.functions.add(g);
        }
        return out;
    }
}
