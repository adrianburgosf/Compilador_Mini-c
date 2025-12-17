package org.example.minic.semantics;

public abstract class Symbol {
    public final String name;
    public final Type type;

    protected Symbol(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    @Override public String toString() { return type + " " + name; }
}
