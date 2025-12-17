package org.example.minic.semantics;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.example.minic.parser.MiniCBaseVisitor;
import org.example.minic.parser.MiniCParser;

/**
 * Chequeo de tipos:
 * - Infere tipos de expresiones.
 * - Valida operadores (aritm., relac., lógicos).
 * - Verifica return vs tipo de función.
 * - Verifica llamadas (aridad y tipos).
 * - Reporta errores en SymbolTable y permite continuar para acumular mensajes.
 */
public class TypeChecker extends MiniCBaseVisitor<Type> {

    private final SymbolTable st;
    private final CollectSymbols cs;

    // tipo inferido por nodo
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
            Scope s = cs.getScopeOf(p); // devuelve null si ese nodo no tiene scope asociado
            if (s != null) return s;
            p = p.getParent();
        }
        // fallback: global
        return st.current();
    }


    // ---------- Programa y funciones ----------

    @Override
    public Type visitProgram(MiniCParser.ProgramContext ctx) {
        for (var child : ctx.children) visit(child);

        // Validar main()
        Symbol s = st.current().resolve("main");
        if (!(s instanceof FuncSymbol f)) {
            st.error("0:0 falta la función de entrada: int main()");
        } else {
            if (f.type != Type.INT) {
                st.error("0:0 main debe ser de tipo int, se encontró " + f.type);
            }
            if (!f.params.isEmpty()) {
                st.error("0:0 main no debe recibir parámetros (se encontraron " + f.params.size() + ")");
            }
        }
        return null;
    }

    @Override
    public Type visitFunctionDecl(MiniCParser.FunctionDeclContext ctx) {
        String name = ctx.ID().getText();
        // Resuelve la función en el scope actual (global)
        Symbol s = st.current().resolve(name);
        if (s instanceof FuncSymbol f) {
            currentFunc = f;
        } else {
            st.error(loc(ctx.ID().getSymbol()) + " función no declarada correctamente: " + name);
            currentFunc = null;
        }

        visit(ctx.block());
        currentFunc = null;
        return null;
    }

    // ---------- Sentencias ----------

    @Override
    public Type visitBlock(MiniCParser.BlockContext ctx) {
        for (var stmt : ctx.stmt()) visit(stmt);
        return null;
    }

    @Override
    public Type visitReturnStmt(MiniCParser.ReturnStmtContext ctx) {
        Type found = Type.VOID;
        if (ctx.expr() != null) {
            found = visit(ctx.expr());
        }
        if (currentFunc != null) {
            if (!Type.compatibleReturn(currentFunc.type, found)) {
                st.error(loc(ctx.getStart()) + " return de tipo " + found +
                        " en función " + currentFunc.name + " de tipo " + currentFunc.type);
            }
        }
        return null;
    }

    @Override
    public Type visitSelectionStmt(MiniCParser.SelectionStmtContext ctx) {
        Type c = visit(ctx.expr());
        if (!Type.isBooly(c)) {
            st.error(loc(ctx.expr().getStart()) + " condición de if debe ser booleana/int (0/1), se obtuvo " + c);
        }
        visit(ctx.stmt(0));
        if (ctx.ELSE() != null) visit(ctx.stmt(1));
        return null;
    }

    @Override
    public Type visitIterationStmt(MiniCParser.IterationStmtContext ctx) {
        Type c = visit(ctx.expr());
        if (!Type.isBooly(c)) {
            st.error(loc(ctx.expr().getStart()) + " condición de while debe ser booleana/int (0/1), se obtuvo " + c);
        }
        visit(ctx.stmt());
        return null;
    }

    @Override
    public Type visitExprStmt(MiniCParser.ExprStmtContext ctx) {
        if (ctx.expr() != null) {
            Type t = visit(ctx.expr());   // chequea la expresión
            set(ctx, t);                  // opcional: propaga tipo al nodo stmt
            return t;                     // devolvemos el tipo de la expr
        }
        // stmt vacío: tipo "void"
        set(ctx, Type.VOID);
        return Type.VOID;
    }


    @Override
    public Type visitVarDecl(MiniCParser.VarDeclContext ctx) {
        Type declT = Type.fromToken(ctx.type().getText());
        for (var id : ctx.initDeclarator()) {
            if (id.expr() != null) {
                Type rhs = visit(id.expr());
                if (!Type.assignmentCompatible(declT, rhs)) {
                    st.error(loc(id.getStart()) + " asignación incompatible: " + declT + " = " + rhs);
                }
            }
        }
        return null;
    }

    // ---------- Expresiones ----------

    @Override
    public Type visitAssignment(MiniCParser.AssignmentContext ctx) {
        if (ctx.ASSIGN() != null) {
            Type lhsT = visit(ctx.logicalOr());
            Type rhsT = visit(ctx.assignment());

            // permitir equivalencias entre INT y BOOL (0/1)
            if ((lhsT == Type.INT && rhsT == Type.BOOL) ||
                    (lhsT == Type.BOOL && rhsT == Type.INT)) {
                rhsT = lhsT; // considera compatible
            }

            if (lhsT != rhsT) {
                st.error(loc(ctx.ASSIGN().getSymbol()) + " asignación incompatible: " + lhsT + " = " + rhsT);
            }
            set(ctx, lhsT);
            return lhsT;
        }
        return visit(ctx.logicalOr());
    }


    @Override
    public Type visitLogicalAnd(MiniCParser.LogicalAndContext ctx) {
        Type left = visit(ctx.equality(0));
        if (!(left == Type.INT || left == Type.BOOL)) {
            st.error(loc(ctx.start) + " && requiere int/bool");
        }
        for (int i = 1; i < ctx.equality().size(); i++) {
            Type right = visit(ctx.equality(i));
            if (!(right == Type.INT || right == Type.BOOL)) {
                st.error(loc(ctx.start) + " && requiere int/bool");
            }
        }
        set(ctx, Type.INT);
        return Type.INT;
    }

    @Override
    public Type visitLogicalOr(MiniCParser.LogicalOrContext ctx) {
        Type left = visit(ctx.logicalAnd(0));
        if (!(left == Type.INT || left == Type.BOOL)) {
            st.error(loc(ctx.start) + " || requiere int/bool");
        }
        for (int i = 1; i < ctx.logicalAnd().size(); i++) {
            Type right = visit(ctx.logicalAnd(i));
            if (!(right == Type.INT || right == Type.BOOL)) {
                st.error(loc(ctx.start) + " || requiere int/bool");
            }
        }
        set(ctx, Type.INT);
        return Type.INT;
    }


    @Override
    public Type visitEquality(MiniCParser.EqualityContext ctx) {
        Type t0 = visit(ctx.relational(0));
        for (int i = 1; i < ctx.relational().size(); i++) {
            Type ti = visit(ctx.relational(i));
            // Permitimos comparar int/bool entre sí (ambos tratables como 0/1)
            boolean ok =
                    (t0 == Type.INT || t0 == Type.BOOL) &&
                            (ti == Type.INT || ti == Type.BOOL);
            if (!ok) {
                st.error(loc(ctx.start) + " comparación ==/!= requiere int/bool compatibles");
            }
        }
        set(ctx, Type.INT);
        return Type.INT;
    }

    @Override
    public Type visitRelational(MiniCParser.RelationalContext ctx) {
        Type t0 = visit(ctx.additive(0));
        for (int i = 1; i < ctx.additive().size(); i++) {
            Type ti = visit(ctx.additive(i));
            // Relacionales definidos sobre enteros (permitimos bool por 0/1 también)
            boolean ok =
                    (t0 == Type.INT || t0 == Type.BOOL) &&
                            (ti == Type.INT || ti == Type.BOOL);
            if (!ok) {
                st.error(loc(ctx.start) + " comparación relacional requiere int/bool");
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
                st.error(loc(ctx.getStart()) + " suma/resta requiere numéricos, se obtuvo "
                        + t + " y " + r);
            }
            t = Type.INT; // normalizamos a INT
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
                st.error(loc(ctx.getStart()) + " mul/div/mod requiere numéricos, se obtuvo "
                        + t + " y " + r);
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
            if (!(t == Type.INT || t == Type.BOOL)) {
                st.error(loc(ctx.NOT().getSymbol()) + " operador ! requiere int/bool");
            }
            set(ctx, Type.INT);
            return Type.INT;
        }
        if (ctx.MINUS() != null) {
            Type t = visit(ctx.unary());
            if (t != Type.INT) {
                st.error(loc(ctx.MINUS().getSymbol()) + " operador unario '-' requiere int");
            }
            set(ctx, Type.INT);
            return Type.INT;
        }
        return visit(ctx.primary());
    }


    @Override
    public Type visitPrimary(MiniCParser.PrimaryContext ctx) {
        // --- Literales por texto ---
        String txt = ctx.getText();
        if (txt != null) {
            if (txt.length() >= 2 && txt.charAt(0) == '"' && txt.charAt(txt.length()-1) == '"') {
                set(ctx, Type.STRING); return Type.STRING;
            }
            if (txt.length() >= 3 && txt.charAt(0) == '\'' && txt.charAt(txt.length()-1) == '\'') {
                set(ctx, Type.CHAR);   return Type.CHAR;
            }
            // true/false como INT (0/1)
            if ("true".equals(txt) || "false".equals(txt)) {
                set(ctx, Type.INT);    return Type.INT;
            }
            if (txt.matches("-?\\d+")) {
                set(ctx, Type.INT);    return Type.INT;
            }
        }

        // --- (expr) ---
        if (ctx.LPAREN() != null && ctx.expr() != null) {
            Type t = visit(ctx.expr());
            set(ctx, t); return t;
        }

        // Helper para scope
        java.util.function.Function<ParseTree, Scope> scopeOf = node -> {
            ParseTree p = node;
            while (p != null) {
                Scope s = cs.getScopeOf(p);
                if (s != null) return s;
                p = p.getParent();
            }
            return st.current();
        };

        // --- Identificador simple (variable) ---
        if (ctx.ID() != null && ctx.LPAREN() == null) {
            String name = ctx.ID().getText();
            Scope scope = scopeOf.apply(ctx);
            Symbol sym = (scope != null) ? scope.resolve(name) : null;
            if (sym instanceof VarSymbol v) {
                set(ctx, v.type); return v.type;
            }
            st.error(loc(ctx.ID().getSymbol()) + " identificador no es variable: " + name);
            set(ctx, Type.INT); return Type.INT;
        }

        // --- Llamada a función ---
        if (ctx.ID() != null && ctx.LPAREN() != null) {
            String fname = ctx.ID().getText();

            java.util.List<MiniCParser.ExprContext> argNodes =
                    (ctx.argList() != null && ctx.argList().expr() != null)
                            ? ctx.argList().expr()
                            : java.util.Collections.emptyList();

            java.util.List<Type> argTypes = new java.util.ArrayList<>();
            for (var e : argNodes) if (e != null) argTypes.add(visit(e));

            // Built-ins
            if ("printInt".equals(fname)) {
                if (argTypes.size() != 1 || argTypes.get(0) != Type.INT)
                    st.error(loc(ctx.ID().getSymbol()) + " printInt espera (int)");
                set(ctx, Type.VOID); return Type.VOID;
            }
            if ("printChar".equals(fname)) {
                if (argTypes.size() != 1 ||
                        !(argTypes.get(0) == Type.CHAR || argTypes.get(0) == Type.INT))
                    st.error(loc(ctx.ID().getSymbol()) + " printChar espera (char) o (int)");
                set(ctx, Type.VOID); return Type.VOID;
            }
            if ("printString".equals(fname)) {
                if (argTypes.size() != 1 || argTypes.get(0) != Type.STRING)
                    st.error(loc(ctx.ID().getSymbol()) + " printString espera (string)");
                set(ctx, Type.VOID); return Type.VOID;
            }

            // Función de usuario
            Scope scope = scopeOf.apply(ctx);
            Symbol s = (scope != null) ? scope.resolve(fname) : null;
            if (!(s instanceof FuncSymbol fs)) {
                st.error(loc(ctx.ID().getSymbol()) + " función no declarada: " + fname);
                set(ctx, Type.INT); return Type.INT;
            }

            if (fs.params.size() != argTypes.size()) {
                st.error(loc(ctx.ID().getSymbol()) + " aridad incorrecta en llamada a " + fname
                        + ": esperaba " + fs.params.size() + " y recibió " + argTypes.size());
            } else {
                for (int i = 0; i < argTypes.size(); i++) {
                    Type expected = fs.params.get(i).type;
                    Type got = argTypes.get(i);
                    if (expected != got) {
                        st.error(loc(ctx.ID().getSymbol()) + " tipo de argumento " + (i+1)
                                + " en " + fname + ": esperado " + expected + " y recibió " + got);
                    }
                }
            }

            set(ctx, fs.type);
            return fs.type;
        }

        // Fallback
        set(ctx, Type.INT);
        return Type.INT;
    }
}
