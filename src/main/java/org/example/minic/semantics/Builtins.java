package org.example.minic.semantics;

import java.util.List;

public final class Builtins {

    private Builtins() {}

    private static void defineIfAbsent(SymbolTable st, String name, Type ret, Type... params) {
        Scope g = st.current(); // global actual
        if (g.resolve(name) == null) {
            FuncSymbol f = new FuncSymbol(name, ret);
            for (int i = 0; i < params.length; i++) {
                f.params.add(new VarSymbol("p"+i, params[i]));
            }
            g.define(f);
        }
    }

    public static void install(SymbolTable st) {
        // camelCase
        defineIfAbsent(st, "printInt",    Type.VOID, Type.INT);
        defineIfAbsent(st, "printChar",   Type.VOID, Type.INT /*aceptamos char/int en TC*/);
        defineIfAbsent(st, "printString", Type.VOID, Type.STRING);
        defineIfAbsent(st, "println",     Type.VOID /*0 args*/);

        // snake_case
        defineIfAbsent(st, "print_int",   Type.VOID, Type.INT);
        defineIfAbsent(st, "print_char",  Type.VOID, Type.INT);
        defineIfAbsent(st, "print_str",   Type.VOID, Type.STRING);
        defineIfAbsent(st, "println",     Type.VOID /* ya definida, no redefine */);
    }
}
