package org.example.minic.semantics;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.example.minic.parser.MiniCBaseVisitor;
import org.example.minic.parser.MiniCParser;

public class CollectSymbols extends MiniCBaseVisitor<Void> {
    private final SymbolTable st;

    // Mapa: nodo del árbol -> scope activo en ese punto
    private final ParseTreeProperty<Scope> scopeOf = new ParseTreeProperty<>();
    public Scope getScopeOf(ParseTree node) { return scopeOf.get(node); }

    public CollectSymbols(SymbolTable st) { this.st = st; }

    @Override
    public Void visitProgram(MiniCParser.ProgramContext ctx) {
        // scope global ya está en la cima de la pila
        scopeOf.put(ctx, st.current());
        return super.visitProgram(ctx);
    }

    @Override
    public Void visitVarDecl(MiniCParser.VarDeclContext ctx) {
        Type t = Type.fromToken(ctx.type().getText());
        for (MiniCParser.InitDeclaratorContext id : ctx.initDeclarator()) {
            String name = id.ID().getText();
            VarSymbol v = new VarSymbol(name, t);
            boolean ok = st.define(v);
            if (!ok) {
                st.error(loc(id.ID().getSymbol()) + " redefinición de variable: " + name);
            }
            // La expresión de inicialización se visita en fases posteriores (tipos/uses)
        }
        return null;
    }

    @Override
    public Void visitFunctionDecl(MiniCParser.FunctionDeclContext ctx) {
        String fname = ctx.ID().getText();
        Type ret = Type.fromToken(ctx.type().getText());
        FuncSymbol f = new FuncSymbol(fname, ret);

        boolean ok = st.define(f);
        if (!ok) {
            st.error(loc(ctx.ID().getSymbol()) + " redefinición de función: " + fname);
        }

        // Scope de función (para parámetros)
        LocalScope fscope = new LocalScope(st.current(), "func " + fname);
        st.push(fscope);
        scopeOf.put(ctx, fscope);

        if (ctx.paramList() != null) {
            for (MiniCParser.ParamContext p : ctx.paramList().param()) {
                Type pt = Type.fromToken(p.type().getText());
                String pname = p.ID().getText();
                VarSymbol ps = new VarSymbol(pname, pt);
                boolean okp = st.define(ps);
                if (!okp) {
                    st.error(loc(p.ID().getSymbol()) + " parámetro duplicado: " + pname);
                }
                f.params.add(ps);
            }
        }

        // Scope del bloque de la función (locals del body)
        LocalScope body = new LocalScope(fscope, "block of " + fname);
        st.push(body);
        scopeOf.put(ctx.block(), body);
        f.locals = body;

        // Visitar el bloque (esto a su vez manejará sub-bloques)
        visit(ctx.block());

        // Cerrar scopes de la función
        st.pop(); // body
        st.pop(); // fscope
        return null;
    }

    @Override
    public Void visitBlock(MiniCParser.BlockContext ctx) {
        // Crear SIEMPRE un nuevo scope para cada { ... }
        LocalScope bs = new LocalScope(st.current(), "block");
        st.push(bs);
        scopeOf.put(ctx, bs);

        // Visitar contenido del bloque
        super.visitBlock(ctx);

        // Cerrar scope del bloque
        st.pop();
        return null;
    }

    // Utilidad para mensajes con línea/columna
    private String loc(Token tok) {
        return String.format("%d:%d", tok.getLine(), tok.getCharPositionInLine());
    }
}
