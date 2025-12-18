package org.example.minic.ir;

import org.example.minic.parser.MiniCBaseVisitor;
import org.example.minic.parser.MiniCParser;

import java.util.ArrayList;
import java.util.List;

public class TacGen extends MiniCBaseVisitor<String> {
    private final TacProgram program = new TacProgram();
    private TacFunction cur;
    private final TempFactory tf = new TempFactory();
    private final LabelFactory lf = new LabelFactory();

    public TacProgram getProgram() { return program; }

    // ---------------- Utilidades TAC ----------------
    private String emit(TacOp op, String a, String b) {
        String t = tf.next();                     // resultado en temporal nuevo
        cur.emit(new TacInstr(op, a, b, t));
        return t;
    }

    private void emitVoid(TacOp op, String a) {
        cur.emit(new TacInstr(op, a, null, null));
    }

    private void mov(String dst, String src) {
        cur.emit(new TacInstr(TacOp.MOV, src, null, dst));
    }

    /** Normaliza una condición a 0/1 devolviendo un temporal */
    private String emitCond(MiniCParser.ExprContext e) {
        String t = visit(e);          // nombre/temporal de la expresión
        if (t == null) t = "0";
        String r = tf.next();
        // r = (t != 0)
        cur.emit(new TacInstr(TacOp.NEQ, t, "0", r));
        return r;
    }

    // --------------- Entradas de alto nivel ---------------
    @Override
    public String visitProgram(MiniCParser.ProgramContext ctx) {
        return super.visitProgram(ctx);
    }

    @Override
    public String visitFunctionDecl(MiniCParser.FunctionDeclContext ctx) {
        String fname = ctx.ID().getText();

        TacFunction f = program.newFunction(fname);
        cur = f;

        // Copiar parámetros
        if (ctx.paramList() != null) {
            for (var p : ctx.paramList().param()) {
                f.params.add(p.ID().getText());
            }
        }

        // Cuerpo
        visit(ctx.block());
        cur = null;
        return null;
    }

    @Override
    public String visitBlock(MiniCParser.BlockContext ctx) {
        for (var s : ctx.stmt()) visit(s);
        return null;
    }

    @Override
    public String visitReturnStmt(MiniCParser.ReturnStmtContext ctx) {
        if (ctx.expr() != null) {
            String v = visit(ctx.expr());
            emitVoid(TacOp.RET, v);
        } else {
            emitVoid(TacOp.RET, null);
        }
        return null;
    }

    @Override
    public String visitExprStmt(MiniCParser.ExprStmtContext ctx) {
        if (ctx.expr() != null) visit(ctx.expr()); // descartar valor
        return null;
    }

    @Override
    public String visitVarDecl(MiniCParser.VarDeclContext ctx) {
        if (cur == null) return null;

        for (var id : ctx.initDeclarator()) {
            if (id.expr() != null) {
                String rhs  = visit(id.expr());
                String name = id.declarator().ID().getText();
                mov(name, rhs);
            }
        }
        return null;
    }

    // ---------------- Expresiones ----------------
    @Override
    public String visitAssignment(MiniCParser.AssignmentContext ctx) {

        // caso: lvalue '=' assignment
        if (ctx.ASSIGN() != null) {
            var lv = ctx.lvalue();
            String rhs = visit(ctx.assignment());

            // por ahora soportamos: x = expr;
            if (lv.expr() == null || lv.expr().isEmpty()) {
                String name = lv.ID().getText();
                mov(name, rhs);
                return name;
            }

            // si es arreglo: a[i] = expr;  (lo implementamos luego)
            throw new RuntimeException("store a arreglo aún no implementado: " + lv.getText());
        }

        // caso: logicalOr
        return visit(ctx.logicalOr());
    }


    @Override
    public String visitLogicalOr(MiniCParser.LogicalOrContext ctx) {
        String v = visit(ctx.logicalAnd(0));
        for (int i = 1; i < ctx.logicalAnd().size(); i++) {
            String rhs = visit(ctx.logicalAnd(i));
            v = emit(TacOp.OR, v, rhs);
        }
        return v;
    }

    @Override
    public String visitLogicalAnd(MiniCParser.LogicalAndContext ctx) {
        String v = visit(ctx.equality(0));
        for (int i = 1; i < ctx.equality().size(); i++) {
            String rhs = visit(ctx.equality(i));
            v = emit(TacOp.AND, v, rhs);
        }
        return v;
    }

    @Override
    public String visitEquality(MiniCParser.EqualityContext ctx) {
        String v = visit(ctx.relational(0));
        List<MiniCParser.RelationalContext> rs = ctx.relational();
        for (int i = 1; i < rs.size(); i++) {
            String rhs = visit(rs.get(i));
            String op = ctx.getChild(2*i-1).getText();
            v = switch (op) {
                case "==" -> emit(TacOp.EQ,  v, rhs);
                case "!=" -> emit(TacOp.NEQ, v, rhs);
                default   -> throw new IllegalStateException("op eq: " + op);
            };
        }
        return v;
    }

    @Override
    public String visitRelational(MiniCParser.RelationalContext ctx) {
        String v = visit(ctx.additive(0));
        List<MiniCParser.AdditiveContext> as = ctx.additive();
        for (int i = 1; i < as.size(); i++) {
            String rhs = visit(as.get(i));
            String op = ctx.getChild(2*i-1).getText();
            v = switch (op) {
                case "<"  -> emit(TacOp.LT, v, rhs);
                case "<=" -> emit(TacOp.LE, v, rhs);
                case ">"  -> emit(TacOp.GT, v, rhs);
                case ">=" -> emit(TacOp.GE, v, rhs);
                default   -> throw new IllegalStateException("op rel: " + op);
            };
        }
        return v;
    }

    @Override
    public String visitAdditive(MiniCParser.AdditiveContext ctx) {
        String v = visit(ctx.multiplicative(0));
        for (int i = 1; i < ctx.multiplicative().size(); i++) {
            String rhs = visit(ctx.multiplicative(i));
            String op = ctx.getChild(2*i-1).getText();
            v = switch (op) {
                case "+" -> emit(TacOp.ADD, v, rhs);
                case "-" -> emit(TacOp.SUB, v, rhs);
                default  -> throw new IllegalStateException("op add: " + op);
            };
        }
        return v;
    }

    @Override
    public String visitMultiplicative(MiniCParser.MultiplicativeContext ctx) {
        String v = visit(ctx.unary(0));
        for (int i = 1; i < ctx.unary().size(); i++) {
            String rhs = visit(ctx.unary(i));
            String op = ctx.getChild(2*i-1).getText();
            v = switch (op) {
                case "*" -> emit(TacOp.MUL, v, rhs);
                case "/" -> emit(TacOp.DIV, v, rhs);
                case "%" -> emit(TacOp.MOD, v, rhs);
                default  -> throw new IllegalStateException("op mul: " + op);
            };
        }
        return v;
    }

    @Override
    public String visitUnary(MiniCParser.UnaryContext ctx) {
        if (ctx.NOT() != null) {
            String v = visit(ctx.unary());
            return emit(TacOp.NOT, v, null);
        }
        if (ctx.MINUS() != null) {
            String v = visit(ctx.unary());
            return emit(TacOp.SUB, "0", v);  // 0 - v
        }
        return visit(ctx.primary());
    }

    @Override
    public String visitPrimary(MiniCParser.PrimaryContext ctx) {
        if (ctx.INT_LIT() != null || ctx.CHAR_LIT() != null ||
                ctx.STR_LIT() != null || ctx.TRUE() != null || ctx.FALSE() != null) {
            return ctx.getText(); // literal directo
        }
        if (ctx.ID() != null && ctx.LPAREN() == null) {
            return ctx.ID().getText(); // variable
        }
        if (ctx.ID() != null && ctx.LPAREN() != null) {
            // llamada
            String fname = ctx.ID().getText();
            List<String> args = new ArrayList<>();
            if (ctx.argList() != null) {
                for (var e : ctx.argList().expr()) args.add(visit(e));
            }
            for (String a : args) emitVoid(TacOp.PARAM, a);
            String t = tf.next();
            cur.emit(new TacInstr(TacOp.CALL, fname, String.valueOf(args.size()), t));
            return t;
        }
        return visit(ctx.expr()); // (expr)
    }

    // ---------------- IF ----------------
    @Override
    public String visitSelectionStmt(MiniCParser.SelectionStmtContext ctx) {
        String cond = visit(ctx.expr());   // cond -> temp
        String Lelse = lf.next();
        String Lend  = lf.next();

        cur.emit(new TacInstr(TacOp.IFZ, cond, Lelse, null)); // ifz cond goto Lelse
        visit(ctx.stmt(0));                                   // then
        cur.emit(new TacInstr(TacOp.GOTO, Lend, null, null)); // goto Lend

        cur.emit(new TacInstr(TacOp.LABEL, Lelse, null, null));
        if (ctx.ELSE() != null) {
            visit(ctx.stmt(1));                               // else
        }
        cur.emit(new TacInstr(TacOp.LABEL, Lend, null, null));
        return null;
    }

    // ---------------- WHILE ----------------
    @Override
    public String visitIterationStmt(MiniCParser.IterationStmtContext ctx) {
        String Lcond = lf.next();
        String Lbody = lf.next();
        String Lend  = lf.next();

        cur.emit(new TacInstr(TacOp.LABEL, Lcond, null, null));
        String cond = visit(ctx.expr());
        cur.emit(new TacInstr(TacOp.IFZ, cond, Lend, null));

        cur.emit(new TacInstr(TacOp.LABEL, Lbody, null, null));
        visit(ctx.stmt());
        cur.emit(new TacInstr(TacOp.GOTO, Lcond, null, null));

        cur.emit(new TacInstr(TacOp.LABEL, Lend, null, null));
        return null;
    }
}
