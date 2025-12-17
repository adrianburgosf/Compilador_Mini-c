package org.example.minic.semantics;

public class LocalScope extends BaseScope {
    private final String name;
    public LocalScope(Scope parent, String name) {
        super(parent);
        this.name = name;
    }
    @Override public String getName() { return name; }
}
