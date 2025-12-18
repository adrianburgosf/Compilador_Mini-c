package org.example.minic.semantics;

import org.example.minic.parser.MiniCBaseVisitor;
import org.example.minic.parser.MiniCParser;

public class CheckUses extends MiniCBaseVisitor<Void> {

    private final SymbolTable st;
    private final CollectSymbols cs;
    private Scope current;

    public CheckUses(SymbolTable st, CollectSymbols cs) {
        this.st = st;
        this.cs = cs;
        this.current = st.current();
    }

    @Override
    public Void visitFunctionDecl(MiniCParser.FunctionDeclContext ctx) {
        Scope saved = current;
        Scope fscope = cs.getScopeOf(ctx);       // scope de params
        if (fscope != null) current = fscope;
        super.visitFunctionDecl(ctx);            // visitará block y demás
        current = saved;
        return null;
    }

    @Override
    public Void visitBlock(MiniCParser.BlockContext ctx) {
        Scope saved = current;
        Scope bs = cs.getScopeOf(ctx);
        if (bs != null) current = bs;
        super.visitBlock(ctx);
        current = saved;
        return null;
    }

    @Override
    public Void visitVarDecl(MiniCParser.VarDeclContext ctx) {
        // revisar inicializadores
        for (var id : ctx.initDeclarator()) {
            if (id.expr() != null) visit(id.expr());
        }
        return null;
    }

    @Override
    public Void visitLvalue(MiniCParser.LvalueContext ctx) {
        String name = ctx.ID().getText();
        Symbol sym = st.resolveFrom(current, name);
        if (!(sym instanceof VarSymbol)) {
            st.error(ctx.ID().getSymbol(), "variable no declarada: " + name);
        }
        // visitar índices: a[i], m[i][j]
        for (var e : ctx.expr()) visit(e);
        return null;
    }

    @Override
    public Void visitPrimary(MiniCParser.PrimaryContext ctx) {
        // llamada: ID '(' argList? ')'
        if (ctx.ID() != null && ctx.LPAREN() != null) {
            String fname = ctx.ID().getText();
            FuncSymbol f = st.resolveFuncGlobal(fname);
            if (f == null) {
                st.error(ctx.ID().getSymbol(), "función no declarada: " + fname);
                return null;
            }
            int got = (ctx.argList() == null) ? 0 : ctx.argList().expr().size();
            int exp = f.params.size();
            if (got != exp) {
                st.error(ctx.ID().getSymbol(),
                        "aridad incorrecta en " + fname + ": esperado " + exp + " recibido " + got);
            }
        }
        return super.visitPrimary(ctx); // para que baje a lvalue/expr/etc
    }
}
