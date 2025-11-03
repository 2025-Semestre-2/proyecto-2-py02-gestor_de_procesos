package so.gui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Consola embebida con soporte de color para mensajes normales y de error.
 * Muestra logs, errores y permite entrada de texto.
 * 
 * Comandos soportados:
 * - help: muestra los comandos disponibles
 * - clear: limpia la pantalla
 * - exit: cierra el JFrame padre
 * 
 * Si esperandoEntrada = true, los comandos no se procesan y la entrada se considera libre.
 * 
 * @author dylan
 */
public class PanelConsola extends JPanel {

    private final JTextPane salida;
    private final JTextField entrada;
    private final StyledDocument doc;

    private final Style estiloNormal;
    private final Style estiloError;

    private boolean esperandoEntrada = false;

    public PanelConsola() {
        setLayout(new BorderLayout());
        setBackground(new Color(240, 240, 240)); // Fondo claro

        // Área de salida
        salida = new JTextPane();
        salida.setEditable(false);
        salida.setBackground(new Color(250, 250, 250));
        salida.setFont(new Font("Consolas", Font.PLAIN, 14));

        doc = salida.getStyledDocument();

        // Definir estilos
        estiloNormal = salida.addStyle("normal", null);
        StyleConstants.setForeground(estiloNormal, Color.DARK_GRAY);

        estiloError = salida.addStyle("error", null);
        StyleConstants.setForeground(estiloError, Color.RED);
        StyleConstants.setBold(estiloError, true);

        // Campo de entrada
        entrada = new JTextField();
        entrada.setBackground(Color.WHITE);
        entrada.setForeground(Color.BLACK);
        entrada.setCaretColor(Color.BLACK);
        entrada.setFont(new Font("Consolas", Font.PLAIN, 14));

        entrada.addActionListener((ActionEvent e) -> procesarEntrada());

        add(new JScrollPane(salida), BorderLayout.CENTER);
        add(entrada, BorderLayout.SOUTH);

        // Redirigir System.out y System.err a la consola
        PrintStream out = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        doc.insertString(doc.getLength(), String.valueOf((char) b), estiloNormal);
                        salida.setCaretPosition(doc.getLength());
                    } catch (BadLocationException ex) {
                        // ignorar
                    }
                });
            }
        });

        System.setOut(out);
        System.setErr(out);

        escribir("Consola iniciada. Escriba 'help' para ver los comandos disponibles.");
    }

    /** Procesa la entrada del usuario. */
    private void procesarEntrada() {
        String texto = entrada.getText().trim();
        entrada.setText("");

        if (texto.isEmpty()) return;

        escribir("> " + texto);

        if (esperandoEntrada) {
            // Modo de entrada libre
            // Aquí podrías guardar el texto o pasarlo a un callback externo
            escribir("Entrada recibida: " + texto);
            esperandoEntrada = false;
            return;
        }

        // Modo comandos
        switch (texto.toLowerCase()) {
            case "help":
                escribir("Comandos disponibles:");
                escribir("  help  - Muestra esta lista de comandos");
                escribir("  clear - Limpia la pantalla");
                escribir("  exit  - Cierra la ventana actual");
                break;

            case "clear":
                limpiar();
                break;

            case "exit":
                cerrarVentanaPadre();
                break;

            default:
                escribirError("Comando desconocido: " + texto);
        }
    }

    /** Escribe texto normal en la consola. */
    public void escribir(String texto) {
        try {
            doc.insertString(doc.getLength(), texto + "\n", estiloNormal);
            salida.setCaretPosition(doc.getLength());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    /** Escribe texto de error (rojo). */
    public void escribirError(String texto) {
        try {
            doc.insertString(doc.getLength(), texto + "\n", estiloError);
            salida.setCaretPosition(doc.getLength());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    /** Escribe el stack trace completo de una excepción en rojo. */
    public void escribirError(Exception e) {
        escribirError("ERROR: " + e.getMessage());
        for (StackTraceElement ste : e.getStackTrace()) {
            escribirError("  at " + ste.toString());
        }
    }

    /** Limpia la consola. */
    public void limpiar() {
        salida.setText("Escribe 'help' para ver los comandos disponibles.");
    }

    /** Cierra la ventana padre que contiene la consola. */
    private void cerrarVentanaPadre() {
        Window ventana = SwingUtilities.getWindowAncestor(this);
        if (ventana != null) {
            escribir("Cerrando ventana...");
            ventana.dispose();
        } else {
            escribirError("No se encontró la ventana padre.");
        }
    }

    /** Devuelve el texto actual del campo de entrada. */
    public String getEntrada() {
        return entrada.getText();
    }

    /** Indica si la consola está esperando entrada de texto. */
    public boolean isEsperandoEntrada() {
        return esperandoEntrada;
    }

    /** Define si la consola está esperando una entrada manual (no comandos). */
    public void setEsperandoEntrada(boolean esperandoEntrada) {
        this.esperandoEntrada = esperandoEntrada;
        if (esperandoEntrada) {
            escribir("Esperando entrada de usuario...");
        }
    }
}
