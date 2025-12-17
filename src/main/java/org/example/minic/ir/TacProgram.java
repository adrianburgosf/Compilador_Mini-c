package org.example.minic.ir;

import java.util.*;

public class TacProgram {
    public final List<TacFunction> functions = new ArrayList<>();

    public TacFunction newFunction(String name) {
        TacFunction f = new TacFunction(name);
        functions.add(f);
        return f;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        for (TacFunction f : functions) sb.append(f).append("\n");
        return sb.toString();
    }
}
