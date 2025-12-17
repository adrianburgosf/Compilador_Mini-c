package org.example.minic.semantics;

import java.util.*;

public class SymbolTable {
    private final Deque<Scope> stack = new ArrayDeque<>();
    private final List<Scope> created = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();

    public SymbolTable() { push(new GlobalScope()); }

    public Scope current() { return stack.peek(); }
    public Scope globals() { return stack.getLast(); }

    public void push(Scope scope) {
        stack.push(scope);
        created.add(scope);
    }
    public void pop() { stack.pop(); }

    // SymbolTable.java
    public void declareBuiltins() {
        FuncSymbol pInt  = new FuncSymbol("printInt",    Type.VOID);
        pInt.params.add(new VarSymbol("x", Type.INT));
        define(pInt); // define en el scope actual (global al inicio)

        FuncSymbol pChar = new FuncSymbol("printChar",   Type.VOID);
        pChar.params.add(new VarSymbol("c", Type.CHAR));
        define(pChar);

        FuncSymbol pStr  = new FuncSymbol("printString", Type.VOID);
        pStr.params.add(new VarSymbol("s", Type.STRING));
        define(pStr);
    }


    public boolean define(Symbol sym) {
        boolean ok = current().define(sym);
        if (!ok) errors.add("redefinición en scope '" + current().getName() + "': " + sym.name);
        return ok;
    }

    public void error(String msg) { errors.add(msg); }

    // --- NUEVO: resoluciones auxiliares ---
    public Symbol resolve(String name) {                 // desde scope actual
        return current().resolve(name);
    }
    public Symbol resolveFrom(Scope scope, String name) { // desde scope dado
        return (scope == null) ? null : scope.resolve(name);
    }
    public FuncSymbol resolveFuncGlobal(String name) {  // solo funciones en global
        Scope g = globals();
        if (g instanceof BaseScope bs) {
            Symbol s = bs.getSymbols().get(name);
            return (s instanceof FuncSymbol f) ? f : null;
        }
        return null;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (Scope sc : created) {
            if (sc instanceof BaseScope bs) {
                sb.append("scope ").append(sc.getName()).append(" {\n");
                for (var e : bs.getSymbols().entrySet()) {
                    sb.append("  ").append(e.getValue()).append("\n");
                }
                sb.append("}\n");
            }
        }
        if (!errors.isEmpty()) {
            sb.append("errors:\n");
            for (var er : errors) sb.append("  - ").append(er).append("\n");
        }
        return sb.toString();
    }
}
