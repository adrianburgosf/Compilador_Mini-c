package org.example.minic.ir;

public class TacInstr {
    public final TacOp op;
    public final String a, b, r; // arg1, arg2, result

    public TacInstr(TacOp op, String a, String b, String r) {
        this.op = op; this.a = a; this.b = b; this.r = r;
    }

    @Override public String toString() {
        return switch (op) {
            case MOV   -> String.format("%s = %s", r, a);
            case RET   -> String.format("ret %s", a == null ? "" : a);
            case ADD   -> String.format("%s = add %s, %s", r, a, b);
            case SUB   -> String.format("%s = sub %s, %s", r, a, b);
            case MUL   -> String.format("%s = mul %s, %s", r, a, b);
            case DIV   -> String.format("%s = div %s, %s", r, a, b);
            case MOD   -> String.format("%s = mod %s, %s", r, a, b);
            case LT    -> String.format("%s = lt %s, %s",  r, a, b);
            case LE    -> String.format("%s = le %s, %s",  r, a, b);
            case GT    -> String.format("%s = gt %s, %s",  r, a, b);
            case GE    -> String.format("%s = ge %s, %s",  r, a, b);
            case EQ    -> String.format("%s = eq %s, %s",  r, a, b);
            case NEQ   -> String.format("%s = neq %s, %s", r, a, b);
            case AND   -> String.format("%s = and %s, %s", r, a, b);
            case OR    -> String.format("%s = or %s, %s",  r, a, b);
            case NOT   -> String.format("%s = not %s",     r, a);
            case PARAM -> String.format("param %s", a);
            case CALL  -> String.format("%s = call %s, %s", r, a, b);

            // memoria
            // LOAD: r = *(base + off)
            case LOAD  -> String.format("%s = load %s, %s", r, a, b);
            // STORE: *(base + off) = a
            case STORE -> String.format("store %s, %s, %s", a, b, r);

            // control de flujo
            case IFZ   -> String.format("ifz %s goto %s", a, b); // a=cond, b=label
            case GOTO  -> String.format("goto %s", a);           // a=label
            case LABEL -> String.format("%s:", a);               // a=label
        };
    }

}
