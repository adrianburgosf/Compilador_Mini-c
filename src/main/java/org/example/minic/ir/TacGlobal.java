package org.example.minic.ir;

/**
 * Declaración global en .data.
 * La usamos para reservar memoria para arreglos globales.
 */
public class TacGlobal {
    public final String name;
    public final int bytes;

    public TacGlobal(String name, int bytes) {
        this.name = name;
        this.bytes = bytes;
    }

    @Override
    public String toString() {
        return ".globl " + name + " : " + bytes + " bytes";
    }
}
