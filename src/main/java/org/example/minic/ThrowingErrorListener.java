package org.example.minic;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException; // <-- IMPORT CORRECTO

public class ThrowingErrorListener extends BaseErrorListener {
    public static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg, RecognitionException e) { // <-- SIN 'throws'
        String src = recognizer.getInputStream().getSourceName();
        String header = (src == null || src.isEmpty())
                ? String.format("%d:%d", line, charPositionInLine)
                : String.format("%s:%d:%d", src, line, charPositionInLine);
        throw new ParseCancellationException(header + " syntax error: " + msg);
    }
}
