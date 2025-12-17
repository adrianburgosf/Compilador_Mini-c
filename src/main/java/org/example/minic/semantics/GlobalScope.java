package org.example.minic.semantics;

public class GlobalScope extends BaseScope {
    public GlobalScope() { super(null); }
    @Override public String getName() { return "global"; }
}
