package org.example.minic.semantics;

import java.util.*;

public class FuncSymbol extends Symbol {
    public final List<VarSymbol> params = new ArrayList<>();
    public Scope locals; // scope del bloque de la función

    public FuncSymbol(String name, Type type) { super(name, type); }

    @Override public String toString() {
        return type + " " + name + "(" +
                String.join(", ", params.stream().map(Object::toString).toList()) + ")";
    }
}
