package org.example.minic.ir;

import java.util.ArrayList;
import java.util.List;

public class TacFunction {
    public final String name;
    public final List<String> params = new ArrayList<>();
    public final List<TacInstr> code = new ArrayList<>();

    public TacFunction(String name) {
        this.name = name;
    }

    public void emit(TacInstr i) { code.add(i); }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("func ").append(name).append(":\n");
        for (TacInstr i : code) {
            sb.append("  ").append(i).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}


