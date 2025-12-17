package org.example.minic.mips;

import java.util.*;

public class MipsEmitter {
    private final StringBuilder sb = new StringBuilder();

    public void emit(String line) {
        sb.append(line).append("\n");
    }

    public void label(String lab) {
        sb.append(lab).append(":\n");
    }

    public String build() {
        return sb.toString();
    }
}
