package org.example.minic.semantics;

import org.antlr.v4.runtime.Token;
import org.example.minic.parser.MiniCBaseVisitor;
import org.example.minic.parser.MiniCParser;

public class CheckUses extends MiniCBaseVisitor<Void> {
    private final SymbolTable st;
    private final CollectSymbols cs;   // <<— necesitamos el mapa de scopes
    private Scope current;             // scope “vivo” al caminar

    public CheckUses(SymbolTable st, CollectSymbols cs) {
        this.st = st;
        this.cs = cs;
        this.current = st.globals(); // comenzamos en global
    }

    private String loc(Token t) { return t.getLine() + ":" + t.getCharPositionInLine(); }

    @Override
    public Void visitProgram(MiniCParser.ProgramContext ctx) {
        current = cs.getScopeOf(ctx); // global
        return super.visitProgram(ctx);
    }

    @Override
    public Void visitFunctionDecl(MiniCParser.FunctionDeclContext ctx) {
        // Entrar al scope del bloque de la función
        Scope prev = current;
        Scope body = cs.getScopeOf(ctx.block());
        if (body != null) current = body;
        super.visitFunctionDecl(ctx);
        current = prev;
        return null;
    }

    @Override
    public Void visitBlock(MiniCParser.BlockContext ctx) {
        Scope prev = current;
        Scope here = cs.getScopeOf(ctx);
        if (here != null) current = here;
        super.visitBlock(ctx);
        current = prev;
        return null;
    }

    @Override
    public Void visitVarDecl(MiniCParser.VarDeclContext ctx) {
        // visit init exprs para usos en RHS
        for (var id : ctx.initDeclarator()) {
            if (id.expr() != null) visit(id.expr());
        }
        return null;
    }

    @Override
    public Void visitPrimary(MiniCParser.PrimaryContext ctx) {
        // variable
        if (ctx.ID() != null && ctx.LPAREN() == null) {
            String name = ctx.ID().getText();
            if (st.resolveFrom(current, name) == null) {
                st.error(loc(ctx.ID().getSymbol()) + " variable no declarada: " + name);
            }
        }
        // llamada
        if (ctx.ID() != null && ctx.LPAREN() != null) {
            String fname = ctx.ID().getText();
            FuncSymbol f = st.resolveFuncGlobal(fname);
            if (f == null) {
                st.error(loc(ctx.ID().getSymbol()) + " función no declarada: " + fname);
            } else {
                int args = (ctx.argList() == null) ? 0 : ctx.argList().expr().size();
                if (args != f.params.size()) {
                    st.error(loc(ctx.ID().getSymbol()) + " llamada a " + fname +
                            " con " + args + " argumentos; esperaba " + f.params.size());
                }
            }
            if (ctx.argList() != null) visit(ctx.argList());
        }
        return null;
    }
}
