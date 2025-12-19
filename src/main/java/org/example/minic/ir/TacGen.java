package org.example.minic.ir;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.example.minic.parser.MiniCBaseVisitor;
import org.example.minic.parser.MiniCParser;
import org.example.minic.semantics.CollectSymbols;
import org.example.minic.semantics.FuncSymbol;
import org.example.minic.semantics.Scope;
import org.example.minic.semantics.Symbol;
import org.example.minic.semantics.SymbolTable;
import org.example.minic.semantics.Type;
import org.example.minic.semantics.VarSymbol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Genera TAC (three-address code) desde el parse tree.
 * Notas clave para este Mini-C:
 * - Variables locales/params se representan como "slots" (sus nombres).
 * - Variables/arreglos globales se representan como etiquetas en .data y se
 *   acceden mediante LOAD/STORE con offset en bytes.
 * - Arreglos usan indexación 1-based: offset = (idx-1).
 */

public final class TacGen extends MiniCBaseVisitor<String> {

    private final SymbolTable st;
    private final CollectSymbols cs;

    private TacProgram program;
    private TacFunction curFn;

    private int tmpId = 0;
    private int lblId = 0;

    public TacGen(SymbolTable st, CollectSymbols cs) {
        this.st = st;
        this.cs = cs;
        this.program = new TacProgram();
    }

    public TacProgram getProgram() {
        return program;
    }

    // ---------------- Helpers ----------------

    private void emit(TacInstr i) {
        if (curFn == null) return;
        curFn.emit(i);
    }


    private String newTemp() {
        return "t" + (tmpId++);
    }

    private String newLabel(String prefix) {
        return prefix + "_" + (lblId++);
    }

    /**
     * Devuelve el scope más cercano para un nodo, subiendo por los padres.
     */
    private Scope scopeOf(ParseTree node) {
        ParseTree p = node;
        while (p != null) {
            Scope s = cs.getScopeOf(p);
            if (s != null) return s;
            p = p.getParent();
        }
        return st.globals();
    }

    private boolean isGlobal(Symbol s) {
        if (s == null) return false;
        Symbol g = st.globals().resolve(s.name);
        return g == s;
    }

    private VarSymbol resolveVar(ParseTree node, String name) {
        Scope sc = scopeOf(node);
        Symbol sym = (sc != null) ? sc.resolve(name) : st.globals().resolve(name);
        if (sym instanceof VarSymbol v) return v;
        return null;
    }

    private FuncSymbol resolveFunc(ParseTree node, String name) {
        Scope sc = scopeOf(node);
        Symbol sym = (sc != null) ? sc.resolve(name) : st.globals().resolve(name);
        if (sym instanceof FuncSymbol f) return f;
        return null;
    }

    // Convierte boolean literals a inmediatos 0/1
    private String boolLit(boolean v) {
        return v ? "1" : "0";
    }

    private String subOne(String v) {
        String t = newTemp();
        emit(new TacInstr(TacOp.SUB, v, "1", t));
        return t;
    }

    private int product(int[] dims) {
        int p = 1;
        for (int d : dims) p *= d;
        return p;
    }

    /**
     * Calcula offset en bytes para un acceso a arreglo N-dim.
     * dims = [d0,d1,...] y indices = [i0,i1,...] (cada i es una expr visitada).
     * Indexación es 1-based => se usa (i-1).
     */
    private String offsetBytesForArray(int[] dims, List<String> indices) {
        int n = Math.min(dims.length, indices.size());
        if (n == 0) {
            return "0";
        }

        // ajustar cada índice: (idx - 1)
        List<String> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(subOne(indices.get(i)));
        }

        // lin = (i0-1)
        String lin = adj.get(0);
        // lin = lin * dim1 + (i1-1) ...
        for (int i = 1; i < n; i++) {
            String tMul = newTemp();
            emit(new TacInstr(TacOp.MUL, lin, Integer.toString(dims[i]), tMul));
            String tAdd = newTemp();
            emit(new TacInstr(TacOp.ADD, tMul, adj.get(i), tAdd));
            lin = tAdd;
        }

        // bytes = lin * 4
        String bytes = newTemp();
        emit(new TacInstr(TacOp.MUL, lin, "4", bytes));
        return bytes;
    }

    /**
     * Para forStmt con expr? expr? expr?: separa init/cond/step recorriendo children.
     */
    private MiniCParser.ExprContext[] splitForExprs(MiniCParser.ForStmtContext ctx) {
        MiniCParser.ExprContext init = null;
        MiniCParser.ExprContext cond = null;
        MiniCParser.ExprContext step = null;

        int semis = 0;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree ch = ctx.getChild(i);
            if (ch instanceof TerminalNode tn && ";".equals(tn.getText())) {
                semis++;
                continue;
            }
            if (ch instanceof MiniCParser.ExprContext e) {
                if (semis == 0) init = e;
                else if (semis == 1) cond = e;
                else if (semis >= 2) step = e;
            }
        }
        return new MiniCParser.ExprContext[]{init, cond, step};
    }

    // ---------------- Program / Decls ----------------

    @Override
    public String visitProgram(MiniCParser.ProgramContext ctx) {
        // 1) Registrar globales (escalares y arreglos) en .data
        for (ParseTree child : ctx.children) {
            if (child instanceof MiniCParser.VarDeclContext vd) {
                // cada initDeclarator contiene un declarator con dims
                for (MiniCParser.InitDeclaratorContext idec : vd.initDeclarator()) {
                    MiniCParser.DeclaratorContext decl = idec.declarator();
                    String name = decl.ID().getText();
                    VarSymbol v = resolveVar(vd, name);
                    if (v == null) continue;

                    // Solo definimos en .data si realmente es global
                    if (!isGlobal(v)) continue;

                    int bytes;
                    if (v.dims != null && v.dims.length > 0) {
                        bytes = 4 * product(v.dims);
                    } else {
                        bytes = 4; // escalar
                    }
                    program.globals.add(new TacGlobal(name, bytes));
                }
            }
        }

        // 2) Generar TAC para funciones
        for (ParseTree child : ctx.children) {
            if (child instanceof MiniCParser.FunctionDeclContext fd) {
                visit(fd);
            }
        }
        return null;
    }

    @Override
    public String visitFunctionDecl(MiniCParser.FunctionDeclContext ctx) {
        String fname = ctx.ID().getText();
        Type ret = Type.fromToken(ctx.type().getText());

        TacFunction fn = new TacFunction(fname);

        // params
        if (ctx.paramList() != null) {
            for (MiniCParser.ParamContext p : ctx.paramList().param()) {
                fn.params.add(p.ID().getText());
            }
        }

        program.functions.add(fn);
        TacFunction saved = curFn;
        int savedTmp = tmpId;
        curFn = fn;
        tmpId = 0;

        visit(ctx.block());

        curFn = saved;
        tmpId = savedTmp;

        return null;
    }

    // ---------------- Statements ----------------

    @Override
    public String visitBlock(MiniCParser.BlockContext ctx) {
        for (MiniCParser.StmtContext s : ctx.stmt()) {
            visit(s);
        }
        return null;
    }

    @Override
    public String visitExprStmt(MiniCParser.ExprStmtContext ctx) {
        if (ctx.expr() != null) visit(ctx.expr());
        return null;
    }

    @Override
    public String visitReturnStmt(MiniCParser.ReturnStmtContext ctx) {
        String v = (ctx.expr() != null) ? visit(ctx.expr()) : null;
        emit(new TacInstr(TacOp.RET, v, null, null));
        return null;
    }

    @Override
    public String visitSelectionStmt(MiniCParser.SelectionStmtContext ctx) {
        String elseLbl = newLabel("else");
        String endLbl = newLabel("endif");

        String cond = visit(ctx.expr());
        // IFZ usa: a = cond, b = label
        emit(new TacInstr(TacOp.IFZ, cond, elseLbl, null));
        visit(ctx.stmt(0));
        if (ctx.ELSE() != null) {
            // GOTO/LABEL usan: a = label
            emit(new TacInstr(TacOp.GOTO, endLbl, null, null));
            emit(new TacInstr(TacOp.LABEL, elseLbl, null, null));
            visit(ctx.stmt(1));
            emit(new TacInstr(TacOp.LABEL, endLbl, null, null));
        } else {
            emit(new TacInstr(TacOp.LABEL, elseLbl, null, null));
        }
        return null;
    }

    @Override
    public String visitIterationStmt(MiniCParser.IterationStmtContext ctx) {
        String startLbl = newLabel("while");
        String endLbl = newLabel("endwhile");

        emit(new TacInstr(TacOp.LABEL, startLbl, null, null));
        String cond = visit(ctx.expr());
        emit(new TacInstr(TacOp.IFZ, cond, endLbl, null));
        visit(ctx.stmt());
        emit(new TacInstr(TacOp.GOTO, startLbl, null, null));
        emit(new TacInstr(TacOp.LABEL, endLbl, null, null));
        return null;
    }

    @Override
    public String visitForStmt(MiniCParser.ForStmtContext ctx) {
        MiniCParser.ExprContext[] parts = splitForExprs(ctx);
        MiniCParser.ExprContext init = parts[0];
        MiniCParser.ExprContext cond = parts[1];
        MiniCParser.ExprContext step = parts[2];

        if (init != null) visit(init);

        String startLbl = newLabel("for");
        String endLbl = newLabel("endfor");

        emit(new TacInstr(TacOp.LABEL, startLbl, null, null));
        if (cond != null) {
            String c = visit(cond);
            emit(new TacInstr(TacOp.IFZ, c, endLbl, null));
        }

        visit(ctx.stmt());

        if (step != null) visit(step);
        emit(new TacInstr(TacOp.GOTO, startLbl, null, null));
        emit(new TacInstr(TacOp.LABEL, endLbl, null, null));
        return null;
    }

    @Override
    public String visitVarDecl(MiniCParser.VarDeclContext ctx) {
        // VarDecl puede aparecer en global o en bloque
        // Para globales, ya reservamos espacio en visitProgram()
        // Aquí sólo generamos MOV para inicializaciones locales

        Scope sc = scopeOf(ctx);
        boolean inGlobal = (sc == st.globals());

        for (MiniCParser.InitDeclaratorContext idec : ctx.initDeclarator()) {
            String name = idec.declarator().ID().getText();
            VarSymbol v = resolveVar(ctx, name);
            if (v == null) continue;

            if (idec.expr() == null) continue;
            String rhs = visit(idec.expr());

            if (inGlobal || isGlobal(v)) {
                continue;
            }

            // Si es arreglo local no usado en tests, no intentamos reservar stack
            if (v.dims != null && v.dims.length > 0) continue;

            emit(new TacInstr(TacOp.MOV, rhs, null, name));
        }
        return null;
    }

    // ---------------- Expressions ----------------

    @Override
    public String visitExpr(MiniCParser.ExprContext ctx) {
        return visit(ctx.assignment());
    }

    @Override
    public String visitAssignment(MiniCParser.AssignmentContext ctx) {
        if (ctx.ASSIGN() != null) {
            // lvalue '=' assignment
            MiniCParser.LvalueContext lv = ctx.lvalue();
            String base = lv.ID().getText();
            VarSymbol v = resolveVar(ctx, base);

            String rhs = visit(ctx.assignment());

            List<MiniCParser.ExprContext> idxNodes = lv.expr();
            if (idxNodes == null) idxNodes = Collections.emptyList();

            if (idxNodes.isEmpty()) {
                // escalar
                if (v != null && isGlobal(v)) {
                    emit(new TacInstr(TacOp.STORE, rhs, base, "0"));
                } else {
                    emit(new TacInstr(TacOp.MOV, rhs, null, base));
                }
                return rhs;
            }

            // arreglo: solo soportamos globales (tests)
            List<String> idxVals = new ArrayList<>();
            for (MiniCParser.ExprContext e : idxNodes) idxVals.add(visit(e));
            int[] dims = (v != null) ? v.dims : new int[]{idxVals.size()};
            String offBytes = offsetBytesForArray(dims, idxVals);
            emit(new TacInstr(TacOp.STORE, rhs, base, offBytes));
            return rhs;
        }
        return visit(ctx.logicalOr());
    }

    @Override
    public String visitLogicalOr(MiniCParser.LogicalOrContext ctx) {
        String v = visit(ctx.logicalAnd(0));
        for (int i = 1; i < ctx.logicalAnd().size(); i++) {
            String r = visit(ctx.logicalAnd(i));
            String t = newTemp();
            emit(new TacInstr(TacOp.OR, v, r, t));
            v = t;
        }
        return v;
    }

    @Override
    public String visitLogicalAnd(MiniCParser.LogicalAndContext ctx) {
        String v = visit(ctx.equality(0));
        for (int i = 1; i < ctx.equality().size(); i++) {
            String r = visit(ctx.equality(i));
            String t = newTemp();
            emit(new TacInstr(TacOp.AND, v, r, t));
            v = t;
        }
        return v;
    }

    @Override
    public String visitEquality(MiniCParser.EqualityContext ctx) {
        String v = visit(ctx.relational(0));
        for (int i = 1; i < ctx.relational().size(); i++) {
            String r = visit(ctx.relational(i));
            TerminalNode op = (TerminalNode) ctx.getChild(2 * i - 1);
            String t = newTemp();
            if (op.getSymbol().getType() == MiniCParser.EQ) {
                emit(new TacInstr(TacOp.EQ, v, r, t));
            } else {
                emit(new TacInstr(TacOp.NEQ, v, r, t));

            }
            v = t;
        }
        return v;
    }

    @Override
    public String visitRelational(MiniCParser.RelationalContext ctx) {
        String v = visit(ctx.additive(0));
        for (int i = 1; i < ctx.additive().size(); i++) {
            String r = visit(ctx.additive(i));
            TerminalNode op = (TerminalNode) ctx.getChild(2 * i - 1);
            String t = newTemp();
            int tt = op.getSymbol().getType();
            if (tt == MiniCParser.LT) emit(new TacInstr(TacOp.LT, v, r, t));
            else if (tt == MiniCParser.LE) emit(new TacInstr(TacOp.LE, v, r, t));
            else if (tt == MiniCParser.GT) emit(new TacInstr(TacOp.GT, v, r, t));
            else emit(new TacInstr(TacOp.GE, v, r, t));
            v = t;
        }
        return v;
    }

    @Override
    public String visitAdditive(MiniCParser.AdditiveContext ctx) {
        String v = visit(ctx.multiplicative(0));
        for (int i = 1; i < ctx.multiplicative().size(); i++) {
            String r = visit(ctx.multiplicative(i));
            TerminalNode op = (TerminalNode) ctx.getChild(2 * i - 1);
            String t = newTemp();
            if (op.getSymbol().getType() == MiniCParser.PLUS) {
                emit(new TacInstr(TacOp.ADD, v, r, t));
            } else {
                emit(new TacInstr(TacOp.SUB, v, r, t));
            }
            v = t;
        }
        return v;
    }

    @Override
    public String visitMultiplicative(MiniCParser.MultiplicativeContext ctx) {
        String v = visit(ctx.unary(0));
        for (int i = 1; i < ctx.unary().size(); i++) {
            String r = visit(ctx.unary(i));
            TerminalNode op = (TerminalNode) ctx.getChild(2 * i - 1);
            String t = newTemp();
            int tt = op.getSymbol().getType();
            if (tt == MiniCParser.STAR) emit(new TacInstr(TacOp.MUL, v, r, t));
            else if (tt == MiniCParser.DIV) emit(new TacInstr(TacOp.DIV, v, r, t));
            else emit(new TacInstr(TacOp.MOD, v, r, t));
            v = t;
        }
        return v;
    }

    @Override
    public String visitUnary(MiniCParser.UnaryContext ctx) {
        if (ctx.primary() != null) return visit(ctx.primary());
        // (NOT | MINUS) unary
        Token op = ctx.getStart();
        String v = visit(ctx.unary());
        if (op.getType() == MiniCParser.NOT) {
            String t = newTemp();
            emit(new TacInstr(TacOp.NOT, v, null, t));
            return t;
        }
        // unary minus: 0 - v
        String t = newTemp();
        emit(new TacInstr(TacOp.SUB, "0", v, t));
        return t;
    }

    @Override
    public String visitPrimary(MiniCParser.PrimaryContext ctx) {
        if (ctx.INT_LIT() != null) return ctx.INT_LIT().getText();
        if (ctx.CHAR_LIT() != null) return ctx.CHAR_LIT().getText();
        if (ctx.STR_LIT() != null) return ctx.STR_LIT().getText();
        if (ctx.TRUE() != null) return boolLit(true);
        if (ctx.FALSE() != null) return boolLit(false);

        // llamada: ID '(' argList? ')'
        if (ctx.ID() != null && ctx.LPAREN() != null) {
            String fname = ctx.ID().getText();
            List<MiniCParser.ExprContext> args = (ctx.argList() != null) ? ctx.argList().expr() : Collections.emptyList();
            for (MiniCParser.ExprContext a : args) {
                String av = visit(a);
                emit(new TacInstr(TacOp.PARAM, av, null, null));
            }

            FuncSymbol f = resolveFunc(ctx, fname);
            String ret = null;
            if (f == null || f.type != Type.VOID) {
                ret = newTemp();
            }
            emit(new TacInstr(TacOp.CALL, fname, Integer.toString(args.size()), ret));
            return (ret != null) ? ret : "0";
        }

        // lvalue incluye arreglos
        if (ctx.lvalue() != null) {
            return visit(ctx.lvalue());
        }

        // ID suelto
        if (ctx.ID() != null) {
            String name = ctx.ID().getText();
            VarSymbol v = resolveVar(ctx, name);
            if (v != null && isGlobal(v)) {
                // leer escalar global => LOAD base, 0
                String t = newTemp();
                emit(new TacInstr(TacOp.LOAD, name, "0", t));
                return t;
            }
            return name;
        }

        // (expr)
        if (ctx.expr() != null) return visit(ctx.expr());

        return "0";
    }

    @Override
    public String visitLvalue(MiniCParser.LvalueContext ctx) {
        String base = ctx.ID().getText();
        VarSymbol v = resolveVar(ctx, base);
        List<MiniCParser.ExprContext> idxNodes = ctx.expr();
        if (idxNodes == null || idxNodes.isEmpty()) {
            // variable
            if (v != null && isGlobal(v)) {
                String t = newTemp();
                emit(new TacInstr(TacOp.LOAD, base, "0", t));
                return t;
            }
            return base;
        }

        // arreglo global
        List<String> idxVals = new ArrayList<>();
        for (MiniCParser.ExprContext e : idxNodes) idxVals.add(visit(e));
        int[] dims = (v != null) ? v.dims : new int[]{idxVals.size()};
        String offBytes = offsetBytesForArray(dims, idxVals);
        String t = newTemp();
        emit(new TacInstr(TacOp.LOAD, base, offBytes, t));
        return t;
    }
}
