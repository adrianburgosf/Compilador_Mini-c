package org.example.minic.semantics;

import java.util.Arrays;

public class VarSymbol extends Symbol {
    // dims.length == 0 => escalar
    public final int[] dims;

    public VarSymbol(String name, Type type) {
        super(name, type);
        this.dims = new int[0];
    }

    public VarSymbol(String name, Type type, int[] dims) {
        super(name, type);
        this.dims = (dims == null) ? new int[0] : dims;
    }

    public boolean isArray() { return dims.length > 0; }
    public int rank() { return dims.length; }

    @Override public String toString() {
        if (!isArray()) return super.toString();
        return type + " " + name + Arrays.toString(dims);
    }
}
