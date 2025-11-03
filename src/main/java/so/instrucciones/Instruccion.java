package so.instrucciones;

import java.util.List;

/**
 * Representa una instrucción ensamblador analizada.
 *
 * @author dylan
 */
public class Instruccion {

    private final CodigoOperacion codigoOperacion;
    private final List<String> operandos;

    public Instruccion(CodigoOperacion codigoOperacion, List<String> operandos) {
        this.codigoOperacion = codigoOperacion;
        this.operandos = operandos;
    }

    public CodigoOperacion getCodigoOperacion() {
        return codigoOperacion;
    }

    public List<String> getOperandos() {
        return operandos;
    }

    @Override
    public String toString() {
        return codigoOperacion.name() + (operandos.isEmpty() ? "" : " " + String.join(", ", operandos));
    }
}
