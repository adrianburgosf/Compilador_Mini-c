package org.example.minic;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return (i <= 0) ? name : name.substring(0, i);
    }

    private static void usageAndExit() {
        System.err.println("Usage (required by spec):");
        System.err.println("  minicc <input.mc> -S -o <output.s> [-O] [--dump-ir]");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  -S             : generate MIPS32 assembly (.s/.asm)");
        System.err.println("  -o <file>      : output assembly file (required when -S is used)");
        System.err.println("  -O             : enable IR optimizations (TAC optimizer)");
        System.err.println("  --dump-ir      : print TAC before and after optimization");
        System.err.println();
        System.err.println("Legacy options (kept for compatibility):");
        System.err.println("  --dump-symbols : imprime scopes y símbolos");
        System.err.println("  --check-uses   : valida no declaradas y aridad de llamadas");
        System.err.println("  --emit-tac     : imprime TAC (respeta -O si se pasó)");
        System.err.println("  --emit-mips    : imprime MIPS32 a stdout (respeta -O si se pasó)");
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) usageAndExit();

        // Spec CLI: minicc input.mc -S -o output.s [-O] [--dump-ir]
        boolean emitAsmFile = false;     // -S
        String outAsm = null;            // -o
        boolean optimize = false;        // -O
        boolean dumpIr = false;          // --dump-ir

        // Legacy flags (still useful for debugging)
        boolean dumpSymbols = false;
        boolean checkUses   = false;
        boolean emitTac     = false;
        boolean emitMipsStdout = false;

        String pathStr = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-S" -> emitAsmFile = true;
                case "-o" -> {
                    if (i + 1 >= args.length) usageAndExit();
                    outAsm = args[++i];
                }
                case "-O" -> optimize = true;
                case "--dump-ir" -> dumpIr = true;

                case "--dump-symbols" -> dumpSymbols = true;
                case "--check-uses"   -> checkUses   = true;
                case "--emit-tac"     -> emitTac     = true;
                case "--emit-mips"    -> emitMipsStdout = true;

                default -> {
                    if (a.startsWith("-")) usageAndExit();
                    pathStr = a;
                }
            }
        }

        if (pathStr == null) usageAndExit();
        if (emitAsmFile && (outAsm == null || outAsm.isBlank())) {
            // el formato requerido por la guía siempre incluye -o
            usageAndExit();
        }

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

            // Entrar a semántica si se pidió cualquiera de estas banderas (spec o legacy)
            if (dumpSymbols || checkUses || emitTac || emitMipsStdout || emitAsmFile || dumpIr || optimize) {
                SymbolTable st = new SymbolTable();
                org.example.minic.semantics.Builtins.install(st);

                // 1) Recolección de símbolos
                CollectSymbols collector = new CollectSymbols(st);
                collector.visit(tree);

                // 2) Validación de usos (existencia/ámbito/aridad)
                if (checkUses || emitTac || emitMipsStdout || emitAsmFile || dumpIr) {
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

                // 6) Generación de IR (+ opcionalmente optimización)
                if (emitTac || emitMipsStdout || emitAsmFile || dumpIr) {
                    TacGen gen = new TacGen(st, collector);
                    gen.visit(tree);
                    TacProgram prog = gen.getProgram();

                    if (dumpIr) {
                        System.out.println("=== TAC (before optimization) ===");
                        System.out.print(prog.toString());
                        if (!prog.toString().endsWith("\n")) System.out.println();
                    }

                    TacProgram finalProg = prog;
                    if (optimize) {
                        TacOptimizer opt = new TacOptimizer();
                        finalProg = opt.optimize(prog);
                    }

                    if (dumpIr) {
                        System.out.println("=== TAC (after optimization) ===");
                        System.out.print(finalProg.toString());
                        if (!finalProg.toString().endsWith("\n")) System.out.println();
                    }

                    if (emitTac) {
                        // Por compatibilidad, imprime el TAC final (optimizado solo si -O)
                        System.out.print(finalProg.toString());
                        if (!finalProg.toString().endsWith("\n")) System.out.println();
                    }

                    if (emitMipsStdout || emitAsmFile) {
                        MipsGen mg = new MipsGen();
                        String asm = mg.emitProgram(finalProg);
                        if (emitMipsStdout) {
                            System.out.println(asm);
                        }
                        if (emitAsmFile) {
                            Path outPath = Paths.get(outAsm);
                            Files.writeString(outPath, asm, StandardCharsets.US_ASCII);
                        }
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
