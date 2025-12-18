package org.example.minic.semantics;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.example.minic.parser.MiniCBaseVisitor;
import org.example.minic.parser.MiniCParser;

public class TypeChecker extends MiniCBaseVisitor<Type> {

    private final SymbolTable st;
    private final CollectSymbols cs;

    private final ParseTreeProperty<Type> typeOf = new ParseTreeProperty<>();
    public Type getType(ParseTree n) { return typeOf.get(n); }
    private void set(ParseTree n, Type t) { typeOf.put(n, t); }

    private FuncSymbol currentFunc;

    public TypeChecker(SymbolTable st, CollectSymbols cs) {
        this.st = st;
        this.cs = cs;
    }

    private static String loc(Token t) {
        return String.format("%d:%d", t.getLine(), t.getCharPositionInLine());
    }

    private Scope scopeOf(ParseTree node) {
        ParseTree p = node;
        while (p != null) {
            Scope s = cs.getScopeOf(p);
            if (s != null) return s;
            p = p.getParent();
        }
        return st.current(); // fallback
    }

    @Override
    public Type visitProgram(MiniCParser.ProgramContext ctx) {
        for (var child : ctx.children) visit(child);

        Symbol s = st.current().resolve("main");
        if (!(s instanceof FuncSymbol f)) {
            st.error("0:0 falta la función de entrada: int main()");
        } else {
            if (f.type != Type.INT)
                st.error("0:0 main debe ser de tipo int, se encontró " + f.type);
            if (!f.params.isEmpty())
                st.error("0:0 main no debe recibir parámetros (se encontraron " + f.params.size() + ")");
        }
        return null;
    }

    @Override
    public Type visitFunctionDecl(MiniCParser.FunctionDeclContext ctx) {
        String name = ctx.ID().getText();
        Symbol s = st.current().resolve(name);
        if (s instanceof FuncSymbol f) currentFunc = f;
        else {
            st.error(loc(ctx.ID().getSymbol()) + " función no declarada correctamente: " + name);
            currentFunc = null;
        }
        visit(ctx.block());
        currentFunc = null;
        return null;
    }

    @Override
    public Type visitBlock(MiniCParser.BlockContext ctx) {
        for (var stmt : ctx.stmt()) visit(stmt);
        return null;
    }

    @Override
    public Type visitReturnStmt(MiniCParser.ReturnStmtContext ctx) {
        Type found = Type.VOID;
        if (ctx.expr() != null) found = visit(ctx.expr());
        if (currentFunc != null && !Type.compatibleReturn(currentFunc.type, found)) {
            st.error(loc(ctx.getStart()) + " return de tipo " + found +
                    " en función " + currentFunc.name + " de tipo " + currentFunc.type);
        }
        return null;
    }

    @Override
    public Type visitSelectionStmt(MiniCParser.SelectionStmtContext ctx) {
        Type cond = visit(ctx.expr());
        if (!Type.isBooly(cond))
            st.error(loc(ctx.getStart()) + " condición de if debe ser int/bool, se encontró " + cond);
        visit(ctx.stmt(0));
        if (ctx.ELSE() != null) visit(ctx.stmt(1));
        return null;
    }

    @Override
    public Type visitIterationStmt(MiniCParser.IterationStmtContext ctx) {
        Type cond = visit(ctx.expr());
        if (!Type.isBooly(cond))
            st.error(loc(ctx.getStart()) + " condición de while debe ser int/bool, se encontró " + cond);
        visit(ctx.stmt());
        return null;
    }

    @Override
    public Type visitExprStmt(MiniCParser.ExprStmtContext ctx) {
        if (ctx.expr() != null) visit(ctx.expr());
        return null;
    }

    @Override
    public Type visitVarDecl(MiniCParser.VarDeclContext ctx) {
        Type declared = Type.fromToken(ctx.type().getText());

        for (MiniCParser.InitDeclaratorContext id : ctx.initDeclarator()) {
            String name = id.declarator().ID().getText();

            Scope sc = scopeOf(ctx);
            Symbol sym = (sc != null) ? sc.resolve(name) : null;
            VarSymbol v = (sym instanceof VarSymbol vv) ? vv : null;

            var d = id.declarator();
            boolean isArray = d.LBRACK().size() > 0;

            if (id.expr() != null) {
                Type rhs = visit(id.expr());
                if (isArray) {
                    st.error(loc(d.ID().getSymbol()) + " no se permite inicializar arreglos directamente: " + d.ID().getText());
                } else {
                    if (!Type.assignmentCompatible(declared, rhs)) {
                        st.error(loc(id.getStart()) + " tipo incompatible en init de " + name + ": " + declared + " = " + rhs);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Type visitAssignment(MiniCParser.AssignmentContext ctx) {
        if (ctx.ASSIGN() != null) {
            Type lhsT = visit(ctx.lvalue());
            Type rhsT = visit(ctx.assignment());
            if (!Type.assignmentCompatible(lhsT, rhsT)) {
                st.error(loc(ctx.ASSIGN().getSymbol()) + " asignación incompatible: " + lhsT + " = " + rhsT);
            }
            set(ctx, lhsT);
            return lhsT;
        }
        return visit(ctx.logicalOr());
    }

    @Override
    public Type visitForStmt(MiniCParser.ForStmtContext ctx) {
        // expr? ; expr? ; expr?
        if (ctx.expr(0) != null) visit(ctx.expr(0)); // init
        if (ctx.expr(1) != null) {
            Type cond = visit(ctx.expr(1));
            if (!Type.isBooly(cond)) {
                st.error(loc(ctx.start) + " condición de for debe ser bool/int/char, se encontró: " + cond);
            }
        }
        if (ctx.expr(2) != null) visit(ctx.expr(2)); // update
        visit(ctx.stmt());
        return null;
    }


    @Override
    public Type visitLvalue(MiniCParser.LvalueContext ctx) {
        String name = ctx.ID().getText();
        Scope scope = scopeOf(ctx);
        Symbol sym = (scope != null) ? scope.resolve(name) : null;

        if (!(sym instanceof VarSymbol v)) {
            st.error(loc(ctx.ID().getSymbol()) + " variable no declarada: " + name);
            return Type.INT;
        }

        int idxCount = ctx.expr().size();

        // escalar
        if (!v.isArray()) {
            if (idxCount > 0) {
                st.error(loc(ctx.ID().getSymbol()) + " no puedes indexar un escalar: " + name);
            }
            return v.type;
        }

        // arreglo
        if (idxCount != v.rank()) {
            st.error(loc(ctx.ID().getSymbol()) + " número de índices inválido en " + name +
                    " (tiene " + v.rank() + ", diste " + idxCount + ")");
        }

        for (var e : ctx.expr()) {
            Type ti = visit(e);
            if (!Type.isNumeric(ti)) {
                st.error(loc(ctx.ID().getSymbol()) + " índice no numérico en " + name + ": " + ti);
            }
        }

        // acceder a elemento => tipo base (int/char/bool/string)
        return v.type;
    }


    @Override
    public Type visitLogicalOr(MiniCParser.LogicalOrContext ctx) {
        Type left = visit(ctx.logicalAnd(0));

        // si no hay "||" solo propagamos el tipo
        if (ctx.logicalAnd().size() == 1) {
            set(ctx, left);
            return left;
        }
        // si sí hay "||" validamos booly
        if (!Type.isBooly(left)) {
            st.error(loc(ctx.getStart()) + " operador || requiere int/bool, se obtuvo " + left);
        }
        for (int i = 1; i < ctx.logicalAnd().size(); i++) {
            Type right = visit(ctx.logicalAnd(i));
            if (!Type.isBooly(right)) {
                st.error(loc(ctx.getStart()) + " operador || requiere int/bool, se obtuvo " + right);
            }
        }
        set(ctx, Type.INT);
        return Type.INT;
    }


    @Override
    public Type visitLogicalAnd(MiniCParser.LogicalAndContext ctx) {
        Type left = visit(ctx.equality(0));

        // sin "&&" propagamos tipo
        if (ctx.equality().size() == 1) {
            set(ctx, left);
            return left;
        }
        if (!Type.isBooly(left)) {
            st.error(loc(ctx.getStart()) + " operador && requiere int/bool, se obtuvo " + left);
        }
        for (int i = 1; i < ctx.equality().size(); i++) {
            Type right = visit(ctx.equality(i));
            if (!Type.isBooly(right)) {
                st.error(loc(ctx.getStart()) + " operador && requiere int/bool, se obtuvo " + right);
            }
        }
        set(ctx, Type.INT);
        return Type.INT;
    }


    @Override
    public Type visitEquality(MiniCParser.EqualityContext ctx) {
        Type left = visit(ctx.relational(0));
        // sin == o != propagamos
        if (ctx.relational().size() == 1) {
            set(ctx, left);
            return left;
        }
        for (int i = 1; i < ctx.relational().size(); i++) {
            Type right = visit(ctx.relational(i));
            if (!Type.isBooly(left) || !Type.isBooly(right)) {
                st.error(loc(ctx.getStart()) + " operador ==/!= requiere tipos compatibles");
            }
            left = Type.INT;
        }
        set(ctx, Type.INT);
        return Type.INT;
    }


    @Override
    public Type visitRelational(MiniCParser.RelationalContext ctx) {
        Type left = visit(ctx.additive(0));
        // sin <, <=, >, >= propagamos
        if (ctx.additive().size() == 1) {
            set(ctx, left);
            return left;
        }
        if (!Type.isNumeric(left)) {
            st.error(loc(ctx.getStart()) + " operador relacional requiere int/char");
        }
        for (int i = 1; i < ctx.additive().size(); i++) {
            Type right = visit(ctx.additive(i));
            if (!Type.isNumeric(right)) {
                st.error(loc(ctx.getStart()) + " operador relacional requiere int/char");
            }
        }
        set(ctx, Type.INT);
        return Type.INT;
    }


    @Override
    public Type visitAdditive(MiniCParser.AdditiveContext ctx) {
        Type t = visit(ctx.multiplicative(0));
        for (int i = 1; i < ctx.multiplicative().size(); i++) {
            Type r = visit(ctx.multiplicative(i));
            if (!Type.isNumeric(t) || !Type.isNumeric(r)) {
                st.error(loc(ctx.getStart()) + " suma/resta requiere numéricos, se obtuvo " + t + " y " + r);
            }
            t = Type.INT;
        }
        set(ctx, t);
        return t;
    }

    @Override
    public Type visitMultiplicative(MiniCParser.MultiplicativeContext ctx) {
        Type t = visit(ctx.unary(0));
        for (int i = 1; i < ctx.unary().size(); i++) {
            Type r = visit(ctx.unary(i));
            if (!Type.isNumeric(t) || !Type.isNumeric(r)) {
                st.error(loc(ctx.getStart()) + " mul/div/mod requiere numéricos, se obtuvo " + t + " y " + r);
            }
            t = Type.INT;
        }
        set(ctx, t);
        return t;
    }

    @Override
    public Type visitUnary(MiniCParser.UnaryContext ctx) {
        if (ctx.NOT() != null) {
            Type t = visit(ctx.unary());
            if (!Type.isBooly(t)) st.error(loc(ctx.NOT().getSymbol()) + " operador ! requiere int/bool");
            set(ctx, Type.INT);
            return Type.INT;
        }
        if (ctx.MINUS() != null) {
            Type t = visit(ctx.unary());
            if (t != Type.INT) st.error(loc(ctx.MINUS().getSymbol()) + " operador - requiere int");
            set(ctx, Type.INT);
            return Type.INT;
        }
        Type t = visit(ctx.primary());
        set(ctx, t);
        return t;
    }

    @Override
    public Type visitPrimary(MiniCParser.PrimaryContext ctx) {

        // 1) lvalue (x, a[i], m[i][j]) si existe en tu gramática
        if (ctx.lvalue() != null) {
            Type t = visit(ctx.lvalue());
            set(ctx, t);
            return t;
        }

        // 2) Literales (IMPORTANTE: set(ctx, ...) para que no se quede default)
        if (ctx.INT_LIT() != null) { set(ctx, Type.INT);    return Type.INT; }
        if (ctx.CHAR_LIT() != null){ set(ctx, Type.CHAR);   return Type.CHAR; }
        if (ctx.STR_LIT() != null) { set(ctx, Type.STRING); return Type.STRING; }
        if (ctx.TRUE() != null || ctx.FALSE() != null) { set(ctx, Type.BOOL); return Type.BOOL; }

        // 3) Llamada a función: ID '(' args? ')'
        if (ctx.ID() != null && ctx.LPAREN() != null) {
            String fname = ctx.ID().getText();

            var argNodes = (ctx.argList() != null)
                    ? ctx.argList().expr()
                    : java.util.Collections.<MiniCParser.ExprContext>emptyList();

            var argTypes = new java.util.ArrayList<Type>();
            for (var e : argNodes) argTypes.add(visit(e));

            // built-ins
            if ("println".equals(fname)) {
                if (!argTypes.isEmpty()) st.error(loc(ctx.ID().getSymbol()) + " println no recibe argumentos");
                set(ctx, Type.VOID); return Type.VOID;
            }
            if ("printInt".equals(fname) || "print_int".equals(fname)) {
                if (argTypes.size() != 1 || argTypes.get(0) != Type.INT)
                    st.error(loc(ctx.ID().getSymbol()) + " " + fname + " espera (int)");
                set(ctx, Type.VOID); return Type.VOID;
            }
            if ("printChar".equals(fname) || "print_char".equals(fname)) {
                boolean ok = argTypes.size() == 1 && (argTypes.get(0) == Type.CHAR || argTypes.get(0) == Type.INT);
                if (!ok) st.error(loc(ctx.ID().getSymbol()) + " " + fname + " espera (char)");
                set(ctx, Type.VOID); return Type.VOID;
            }
            if ("printString".equals(fname) || "print_str".equals(fname) || "print_str".equals(fname)) {
                boolean ok = argTypes.size() == 1 && argTypes.get(0) == Type.STRING;
                if (!ok) st.error(loc(ctx.ID().getSymbol()) + " " + fname + " espera (string)");
                set(ctx, Type.VOID); return Type.VOID;
            }

            // función del usuario
            Scope scope = scopeOf(ctx);
            Symbol s = (scope != null) ? scope.resolve(fname) : null;

            if (s instanceof FuncSymbol fs) {
                set(ctx, fs.type);
                return fs.type;
            }

            st.error(loc(ctx.ID().getSymbol()) + " función no declarada: " + fname);
            set(ctx, Type.INT);
            return Type.INT;
        }

        // 4) ID variable (solo si NO es llamada)
        if (ctx.ID() != null) {
            String name = ctx.ID().getText();
            Scope scope = scopeOf(ctx);
            Symbol sym = (scope != null) ? scope.resolve(name) : null;

            if (sym instanceof VarSymbol v) { set(ctx, v.type); return v.type; }

            st.error(loc(ctx.ID().getSymbol()) + " identificador no es variable: " + name);
            set(ctx, Type.INT);
            return Type.INT;
        }

        // 5) (expr)
        if (ctx.expr() != null) {
            Type t = visit(ctx.expr());
            set(ctx, t);
            return t;
        }

        // fallback
        set(ctx, Type.INT);
        return Type.INT;
    }
}
