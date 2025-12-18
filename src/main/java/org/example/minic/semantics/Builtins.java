package org.example.minic.semantics;

import java.util.List;
import org.example.minic.semantics.SymbolTable;

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

    public void declareBuiltins() {
        SymbolTable st = new SymbolTable();
        // --- camelCase (compatibilidad) ---
        FuncSymbol pInt  = new FuncSymbol("printInt",    Type.VOID);
        pInt.params.add(new VarSymbol("x", Type.INT));
        st.define(pInt);

        FuncSymbol pChar = new FuncSymbol("printChar",   Type.VOID);
        pChar.params.add(new VarSymbol("c", Type.CHAR));
        st.define(pChar);

        FuncSymbol pStr  = new FuncSymbol("printString", Type.VOID);
        pStr.params.add(new VarSymbol("s", Type.STRING));
        st.define(pStr);

        // --- snake_case (lo que usas en tu ejemplo) ---
        FuncSymbol p_int  = new FuncSymbol("print_int", Type.VOID);
        p_int.params.add(new VarSymbol("x", Type.INT));
        st.define(p_int);

        FuncSymbol p_char = new FuncSymbol("print_char", Type.VOID);
        p_char.params.add(new VarSymbol("c", Type.CHAR));
        st.define(p_char);

        FuncSymbol p_str  = new FuncSymbol("print_str", Type.VOID);
        p_str.params.add(new VarSymbol("s", Type.STRING));
        st.define(p_str);

        FuncSymbol nl = new FuncSymbol("println", Type.VOID);
        st.define(nl);
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
