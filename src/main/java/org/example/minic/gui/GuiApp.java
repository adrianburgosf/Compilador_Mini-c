package org.example.minic.gui;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.example.minic.ThrowingErrorListener;
import org.example.minic.ir.TacGen;
import org.example.minic.ir.TacProgram;
import org.example.minic.mips.MipsGen;
import org.example.minic.parser.MiniCLexer;
import org.example.minic.parser.MiniCParser;
import org.example.minic.semantics.CheckUses;
import org.example.minic.semantics.CollectSymbols;
import org.example.minic.semantics.SymbolTable;
import org.example.minic.semantics.TypeChecker;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

public class GuiApp {

    // === Configurable: ruta del simulador ===
    // Ajusta la ruta a tu instalación:
    // Ejemplos:
    //  - PCSpim (x86): "C:\\Program Files (x86)\\PCSpim\\PCSpim.exe"
    //  - QtSpim: "C:\\Program Files\\QtSpim\\QtSpim.exe"
    private String pcspimPath = "C:\\Program Files (x86)\\PCSpim\\PCSpim.exe";

    // --- UI ---
    private JFrame frame;
    private JTextArea srcArea;
    private JTextArea outArea;
    private JTextArea tacArea;
    private JTextArea mipsArea;
    private JTextArea symsArea;
    private JTextArea logArea;

    private JCheckBox cbDump, cbCheckUses, cbEmitTac, cbEmitMips;

    // Último archivo abierto/guardado
    private File currentFile = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GuiApp().start());
    }

    private void start() {
        frame = new JFrame("MiniC Compiler GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 800);

        // Menú
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem miOpen = new JMenuItem("Open .mc…");
        miOpen.addActionListener(e -> onOpen());
        JMenuItem miSaveAsm = new JMenuItem("Save MIPS (.asm)...");
        miSaveAsm.addActionListener(e -> onSaveAsm());
        JMenuItem miExit = new JMenuItem("Exit");
        miExit.addActionListener(e -> frame.dispose());

        fileMenu.add(miOpen);
        fileMenu.add(miSaveAsm);
        fileMenu.addSeparator();
        fileMenu.add(miExit);
        menuBar.add(fileMenu);

        JMenu actionsMenu = new JMenu("Actions");
        JMenuItem miCompile = new JMenuItem("Compile");
        miCompile.setAccelerator(KeyStroke.getKeyStroke("F5"));
        miCompile.addActionListener(e -> onCompile());
        JMenuItem miRun = new JMenuItem("Run PCSpim");
        miRun.addActionListener(e -> onRunPcspim());
        actionsMenu.add(miCompile);
        actionsMenu.add(miRun);
        menuBar.add(actionsMenu);

        frame.setJMenuBar(menuBar);

        // Panel Superior
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.setBorder(new EmptyBorder(6, 6, 6, 6));

        cbDump = new JCheckBox("Dump Symbols");
        cbCheckUses = new JCheckBox("Check Uses");
        cbEmitTac = new JCheckBox("Emit TAC");
        cbEmitMips = new JCheckBox("Emit MIPS");
        // Por defecto todo activado
        cbDump.setSelected(true);
        cbCheckUses.setSelected(true);
        cbEmitTac.setSelected(true);
        cbEmitMips.setSelected(true);

        JButton btnCompile = new JButton("Compile (F5)");
        btnCompile.addActionListener(e -> onCompile());

        JButton btnOpen = new JButton("Open");
        btnOpen.addActionListener(e -> onOpen());

        JButton btnSaveAsm = new JButton("Save .asm");
        btnSaveAsm.addActionListener(e -> onSaveAsm());

        JButton btnRun = new JButton("Run PCSpim");
        btnRun.addActionListener(e -> onRunPcspim());

        topBar.add(cbDump);
        topBar.add(cbCheckUses);
        topBar.add(cbEmitTac);
        topBar.add(cbEmitMips);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(btnOpen);
        topBar.add(btnCompile);
        topBar.add(btnSaveAsm);
        topBar.add(btnRun);

        // Área de edición
        srcArea = new JTextArea();
        srcArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JScrollPane srcScroll = new JScrollPane(srcArea);

        // Pestañas de resultados
        outArea  = mkMonoArea();
        tacArea  = mkMonoArea();
        mipsArea = mkMonoArea();
        symsArea = mkMonoArea();
        logArea  = mkMonoArea();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Output (CLI-like)", new JScrollPane(outArea));
        tabs.addTab("TAC",               new JScrollPane(tacArea));
        tabs.addTab("MIPS",              new JScrollPane(mipsArea));
        tabs.addTab("Symbols",           new JScrollPane(symsArea));
        tabs.addTab("Log / Errors",      new JScrollPane(logArea));

        // Split pane
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, srcScroll, tabs);
        split.setDividerLocation(500);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(topBar, BorderLayout.NORTH);
        frame.getContentPane().add(split, BorderLayout.CENTER);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JTextArea mkMonoArea() {
        JTextArea a = new JTextArea();
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        a.setEditable(false);
        return a;
    }

    // --- Actions ---

    private void onOpen() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open MiniC source (.mc)");
        if (currentFile != null) fc.setCurrentDirectory(currentFile.getParentFile());
        int r = fc.showOpenDialog(frame);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                String txt = Files.readString(f.toPath());
                srcArea.setText(txt);
                currentFile = f;
                frame.setTitle("MiniC Compiler GUI — " + f.getName());
                log("Opened: " + f.getAbsolutePath());
            } catch (IOException ex) {
                error("Failed to open file: " + ex.getMessage());
            }
        }
    }

    private void onSaveAsm() {
        if (mipsArea.getText().isBlank()) {
            info("No MIPS to save. Generate it first (Emit MIPS).");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save MIPS (.asm)");
        if (currentFile != null) {
            fc.setSelectedFile(new File(
                    Objects.requireNonNull(currentFile.getParentFile()),
                    stripExt(currentFile.getName()) + ".asm"));
        }
        int r = fc.showSaveDialog(frame);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try (Writer w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.US_ASCII))) {
                w.write(mipsArea.getText());
                log("Saved ASM: " + f.getAbsolutePath());
            } catch (IOException ex) {
                error("Failed to save .asm: " + ex.getMessage());
            }
        }
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return (i <= 0) ? name : name.substring(0, i);
    }

    private void onCompile() {
        final String src = srcArea.getText();
        if (src.isBlank()) {
            info("Source is empty.");
            return;
        }

        // Limpia salidas
        outArea.setText("");
        tacArea.setText("");
        mipsArea.setText("");
        symsArea.setText("");
        logArea.setText("");

        final boolean dumpSymbols = cbDump.isSelected();
        final boolean checkUses   = cbCheckUses.isSelected();
        final boolean emitTac     = cbEmitTac.isSelected();
        final boolean emitMips    = cbEmitMips.isSelected();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                compileInMemory(src, dumpSymbols, checkUses, emitTac, emitMips);
                return null;
            }
        }.execute();
    }

    private void onRunPcspim() {
        // Si no hay MIPS en pantalla, compila forzosamente con MIPS
        if (mipsArea.getText().isBlank()) {
            cbEmitMips.setSelected(true);
            onCompile();
            // Espera mínima a que termine el SwingWorker (sencillo)
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            if (mipsArea.getText().isBlank()) {
                error("No MIPS generated.");
                return;
            }
        }

        // Guarda a archivo temporal y lanza PCSpim
        try {
            File asmTmp = File.createTempFile("minic_", ".asm");
            try (Writer w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(asmTmp), StandardCharsets.US_ASCII))) {
                w.write(mipsArea.getText());
            }
            log("Temp ASM: " + asmTmp.getAbsolutePath());

            if (pcspimPath == null || !(new File(pcspimPath).exists())) {
                error("PCSpim/QtSpim not found. Adjust 'pcspimPath' in GuiApp.");
                return;
            }

            // PCSpim acepta: PCSpim.exe -file <asm>
            ProcessBuilder pb = new ProcessBuilder(pcspimPath, "-file", asmTmp.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            log("Launched PCSpim.");
            // (opcional) leer salida del proceso
        } catch (IOException ex) {
            error("Failed to run PCSpim: " + ex.getMessage());
        }
    }

    // --- Core pipeline (similar a Main), pero con String source ---
    private void compileInMemory(String source,
                                 boolean dumpSymbols, boolean checkUses, boolean emitTac, boolean emitMips) {
        try {
            CharStream input = CharStreams.fromString(source);

            MiniCLexer lexer = new MiniCLexer(input);
            lexer.removeErrorListeners();
            lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            MiniCParser parser = new MiniCParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(ThrowingErrorListener.INSTANCE);

            ParseTree tree = parser.program();

            if (dumpSymbols || checkUses || emitTac || emitMips) {
                SymbolTable st = new SymbolTable();
                org.example.minic.semantics.Builtins.install(st);
                CollectSymbols collector = new CollectSymbols(st);
                collector.visit(tree);

                if (checkUses || emitTac || emitMips) {
                    new CheckUses(st, collector).visit(tree);
                }

                TypeChecker typer = new TypeChecker(st, collector);
                typer.visit(tree);

                if (dumpSymbols) {
                    symsArea.setText(st.dump());
                }

                if (!st.errors.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String e : st.errors) sb.append(e).append("\n");
                    logArea.setText(sb.toString());
                    outArea.setText("Se detectaron errores; se omite generación de IR/MIPS.\n");
                    return;
                }

                if (emitTac || emitMips) {
                    TacGen gen = new TacGen();
                    gen.visit(tree);
                    TacProgram prog = gen.getProgram();

                    if (emitTac) {
                        tacArea.setText(prog.toString());
                    }
                    if (emitMips) {
                        MipsGen mg = new MipsGen();
                        String asm = mg.emitProgram(prog);
                        mipsArea.setText(asm);
                    }
                    outArea.setText("OK\n");
                    return;
                }

                outArea.setText("OK (análisis completado)\n");
            } else {
                outArea.setText(tree.toStringTree(parser));
            }

        } catch (ParseCancellationException ex) {
            logArea.setText(ex.getMessage() + "\n");
            outArea.setText("ERROR\n");
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            logArea.setText(sw.toString());
            outArea.setText("ERROR\n");
        }
    }

    // --- Helpers log ---
    private void log(String s)  { logArea.append("[INFO] " + s + "\n"); }
    private void info(String s) { JOptionPane.showMessageDialog(frame, s, "Info", JOptionPane.INFORMATION_MESSAGE); }
    private void error(String s){ JOptionPane.showMessageDialog(frame, s, "Error", JOptionPane.ERROR_MESSAGE); }
}
