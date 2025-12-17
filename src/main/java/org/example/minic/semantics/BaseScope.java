package org.example.minic.semantics;

import java.util.*;

public abstract class BaseScope implements Scope {
    protected final Scope enclosingScope;
    protected final Map<String, Symbol> symbols = new LinkedHashMap<>();

    protected BaseScope(Scope enclosingScope) { this.enclosingScope = enclosingScope; }

    @Override public Scope getEnclosingScope() { return enclosingScope; }

    @Override public Symbol resolve(String name) {
        Symbol s = symbols.get(name);
        if (s != null) return s;
        return enclosingScope != null ? enclosingScope.resolve(name) : null;
    }

    @Override public boolean define(Symbol sym) {
        if (symbols.containsKey(sym.name)) return false;
        symbols.put(sym.name, sym);
        return true;
    }

    public Map<String, Symbol> getSymbols() { return symbols; }
}
