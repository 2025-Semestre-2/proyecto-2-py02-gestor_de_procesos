package utils;

import javax.swing.text.*;

public class NumericDocumentFilter extends DocumentFilter {

    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
            throws BadLocationException {
        if (string == null) return;

        String nuevoTexto = new StringBuilder(fb.getDocument().getText(0, fb.getDocument().getLength()))
                .insert(offset, string)
                .toString();

        if (esNumeroValido(nuevoTexto)) {
            super.insertString(fb, offset, string, attr);
        }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        if (text == null) return;

        String actual = fb.getDocument().getText(0, fb.getDocument().getLength());
        String nuevoTexto = actual.substring(0, offset) + text + actual.substring(offset + length);

        if (esNumeroValido(nuevoTexto)) {
            super.replace(fb, offset, length, text, attrs);
        }
    }

    private boolean esNumeroValido(String texto) {
        // Vacío es válido (permite borrar)
        if (texto.isEmpty()) return true;

        // Solo dígitos
        if (!texto.matches("\\d+")) return false;

        // No puede empezar con 0 ni ser "0"
        if (texto.startsWith("0")) return false;

        return true;
    }
}