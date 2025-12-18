package org.example.minic;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.example.minic.parser.MiniCLexer;
import org.example.minic.parser.MiniCParser;
import org.example.minic.semantics.CollectSymbols;
import org.example.minic.semantics.SymbolTable;
import org.example.minic.semantics.TypeChecker;
import org.example.minic.semantics.CheckUses;
import org.example.minic.ir.TacGen;
import org.example.minic.ir.TacProgram;
import org.example.minic.ir.TacOptimizer;   // <-- NUEVO
import org.example.minic.mips.MipsGen;

public class Main {

    private static void usageAndExit() {
        System.err.println("Usage: minicc [--dump-symbols] [--check-uses] [--emit-tac] [--emit-mips] <file.mc>");
        System.err.println("  --dump-symbols : imprime scopes y símbolos");
        System.err.println("  --check-uses   : valida no declaradas y aridad de llamadas");
        System.err.println("  --emit-tac     : genera y muestra TAC (optimizado)");
        System.err.println("  --emit-mips    : genera y muestra MIPS32 (desde TAC optimizado)");
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) usageAndExit();

        boolean dumpSymbols = false;
        boolean checkUses   = false;
        boolean emitTac     = false;
        boolean emitMips    = false;
        String  pathStr     = null;

        for (String a : args) {
            switch (a) {
                case "--dump-symbols" -> dumpSymbols = true;
                case "--check-uses"   -> checkUses   = true;
                case "--emit-tac"     -> emitTac     = true;
                case "--emit-mips"    -> emitMips    = true;
                default               -> pathStr     = a;
            }
        }
        if (pathStr == null) usageAndExit();

        Path path = Paths.get(pathStr);
        CharStream input = CharStreams.fromPath(path);

        MiniCLexer lexer = new MiniCLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        MiniCParser parser = new MiniCParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(ThrowingErrorListener.INSTANCE);

        try {
            ParseTree tree = parser.program();

            // Entrar a semántica si se pidió cualquiera de estas banderas
            if (dumpSymbols || checkUses || emitTac || emitMips) {
                SymbolTable st = new SymbolTable();
                org.example.minic.semantics.Builtins.install(st);

                // 1) Recolección de símbolos
                CollectSymbols collector = new CollectSymbols(st);
                collector.visit(tree);

                // 2) Validación de usos (existencia/ámbito/aridad)
                if (checkUses || emitTac || emitMips) {
                    new CheckUses(st, collector).visit(tree);
                }

                // 3) Chequeo de tipos
                TypeChecker typer = new TypeChecker(st, collector);
                typer.visit(tree);

                // 4) Dump de símbolos (si se pidió)
                if (dumpSymbols) {
                    System.out.println(st.dump());
                }

                // 5) Si hubo errores, no generamos IR/MIPS
                if (!st.errors.isEmpty()) {
                    for (String msg : st.errors) System.err.println(msg);
                    System.err.println("Se detectaron errores; se omite generación de IR/MIPS.");
                    System.exit(3);
                }

                // 6) Generación de IR y optimización
                if (emitTac || emitMips) {
                    TacGen gen = new TacGen();
                    gen.visit(tree);
                    TacProgram prog = gen.getProgram();

                    // OPT: aplicar optimizaciones (const folding, mov redundante, etc.)
                    TacOptimizer opt = new TacOptimizer();
                    TacProgram optProg = opt.optimize(prog);

                    if (emitTac) {
                        System.out.println(optProg.toString());   // imprimir TAC optimizado
                    }
                    if (emitMips) {
                        MipsGen mg = new MipsGen();
                        String asm = mg.emitProgram(optProg);     // emitir desde TAC optimizado
                        System.out.println(asm);
                    }
                    return;
                }

                // si solo pediste dump/check y nada más, salimos limpio
                return;
            } else {
                // Sin banderas: muestra el árbol (útil para debug)
                System.out.println(tree.toStringTree(parser));
            }
        } catch (ParseCancellationException ex) {
            System.err.println(ex.getMessage());
            System.exit(2);
        }
    }
}
