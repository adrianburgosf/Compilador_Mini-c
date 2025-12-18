package org.example.minic.semantics;

import org.antlr.v4.runtime.Token;
import org.example.minic.parser.MiniCBaseVisitor;
import org.example.minic.parser.MiniCParser;

import java.util.ArrayList;
import java.util.List;

public class CollectSymbols extends MiniCBaseVisitor<Void> {

    private final SymbolTable st;
    private final java.util.Map<Object, Scope> scopeOf = new java.util.HashMap<>();

    public CollectSymbols(SymbolTable st) { this.st = st; }

    public Scope getScopeOf(Object node) { return scopeOf.get(node); }

    @Override
    public Void visitProgram(MiniCParser.ProgramContext ctx) {
        scopeOf.put(ctx, st.current()); // global
        return super.visitProgram(ctx);
    }

    private int[] parseDims(MiniCParser.DeclaratorContext dec) {
        // declarator : ID ('[' INT_LIT ']')*
        List<Integer> dims = new ArrayList<>();
        for (var lit : dec.INT_LIT()) {
            try { dims.add(Integer.parseInt(lit.getText())); }
            catch (Exception ignored) { dims.add(0); }
        }
        int[] out = new int[dims.size()];
        for (int i = 0; i < dims.size(); i++) out[i] = dims.get(i);
        return out;
    }

    @Override
    public Void visitVarDecl(MiniCParser.VarDeclContext ctx) {
        Type t = Type.fromToken(ctx.type().getText());

        for (var id : ctx.initDeclarator()) {
            var dec = id.declarator();
            String name = dec.ID().getText();
            int[] dims = parseDims(dec);

            VarSymbol v = new VarSymbol(name, t, dims);

            if (!st.define(v)) {
                Token tok = dec.ID().getSymbol();
                st.error(tok, "redefinición de variable: " + name);
            }
        }
        return null;
    }

    @Override
    public Void visitFunctionDecl(MiniCParser.FunctionDeclContext ctx) {
        String name = ctx.ID().getText();
        Type ret = Type.fromToken(ctx.type().getText());

        FuncSymbol f = new FuncSymbol(name, ret);
        if (!st.define(f)) {
            st.error(ctx.ID().getSymbol(), "redefinición de función: " + name);
            return null;
        }

        // scope de parámetros (padre del bloque)
        Scope fscope = new LocalScope(st.current(), "func " + name);
        scopeOf.put(ctx, fscope);

        st.push(fscope);

        if (ctx.paramList() != null) {
            for (var p : ctx.paramList().param()) {
                String pname = p.ID().getText();
                Type pt = Type.fromToken(p.type().getText());
                VarSymbol pv = new VarSymbol(pname, pt);

                if (!st.define(pv)) {
                    st.error(p.ID().getSymbol(), "parámetro duplicado: " + pname);
                }
                f.params.add(pv);
            }
        }

        // el bloque crea su propio scope en visitBlock()
        visit(ctx.block());
        f.locals = scopeOf.get(ctx.block());

        st.pop(); // fscope
        return null;
    }

    @Override
    public Void visitBlock(MiniCParser.BlockContext ctx) {
        Scope bs = new LocalScope(st.current(), "block");
        scopeOf.put(ctx, bs);
        st.push(bs);
        super.visitBlock(ctx);
        st.pop();
        return null;
    }
}
