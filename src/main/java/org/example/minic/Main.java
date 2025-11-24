package org.example.minic;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.file.*;

import org.example.minic.parser.MiniCLexer;
import org.example.minic.parser.MiniCParser;
import org.antlr.v4.runtime.misc.ParseCancellationException;


public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: minicc <file.mc>");
            System.exit(1);
        }
        Path path = Paths.get(args[0]);
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
            System.out.println(tree.toStringTree(parser)); // temporal
        } catch (ParseCancellationException ex) {
            System.err.println(ex.getMessage());
            System.exit(2);
        }
    }
}

