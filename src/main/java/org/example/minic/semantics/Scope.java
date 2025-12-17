package org.example.minic.semantics;

public interface Scope {
    String getName();
    Scope getEnclosingScope();
    Symbol resolve(String name);
    boolean define(Symbol sym); // false si redefinición en este mismo scope
}
