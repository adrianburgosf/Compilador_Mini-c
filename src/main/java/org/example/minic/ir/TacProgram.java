package org.example.minic.ir;

import java.util.*;

public class TacProgram {
    /** globals en .data (por ahora: arreglos globales) */
    public final List<TacGlobal> globals = new ArrayList<>();
    public final List<TacFunction> functions = new ArrayList<>();

    public TacFunction newFunction(String name) {
        TacFunction f = new TacFunction(name);
        functions.add(f);
        return f;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!globals.isEmpty()) {
            sb.append("# globals\n");
            for (TacGlobal g : globals) sb.append(g).append("\n");
            sb.append("\n");
        }
        for (TacFunction f : functions) sb.append(f).append("\n");
        return sb.toString();
    }
}
