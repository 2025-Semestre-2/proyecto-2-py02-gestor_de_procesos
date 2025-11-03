package so.instrucciones;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author dylan
 */
public class InstruccionParser {
    public static Instruccion parse(String line) {
        if (line == null) {
            return null;
        }
        line = line.trim();

        // Ignorar comentarios, etiquetas o líneas vacías
        if (line.isEmpty() || line.endsWith(":") || line.startsWith(";")) {
            return null;
        }

        String[] parts = line.split("\\s+", 2);
        String mnemonic = parts[0];
        CodigoOperacion opcode = CodigoOperacion.fromString(mnemonic);

        List<String> operands = new ArrayList<>();
        if (parts.length > 1) {
            String operandPart = parts[1].trim();
            operands = parseOperands(operandPart);
        }

        validateOperands(opcode, operands);
        return new Instruccion(opcode, operands);
    }

    private static List<String> parseOperands(String operandPart) {
        List<String> operands = new ArrayList<>();
        for (String op : operandPart.split(",")) {
            op = op.trim();
            if (!op.isEmpty()) {
                operands.add(op);
            }
        }
        return operands;
    }

    private static void validateOperands(CodigoOperacion opcode, List<String> operands) {
        int count = operands.size();

        // Verificar número esperado
        if (opcode.getOperandos() >= 0 && opcode.getOperandos() != count) {
            throw new IllegalArgumentException("Número inválido de operandos para " + opcode + ": se esperaban " + opcode.getOperandos() + ", se recibieron " + count);
        }

        switch (opcode) {
            case MOV ->
                validateMOV(operands);
            case ADD, SUB ->
                validateSingleRegister(operands);
            case LOAD, STORE, PUSH, POP ->
                validateRegister(operands.get(0));
            case INC, DEC ->
                validateIncDec(opcode, operands);
            case CMP, SWAP ->
                validateTwoRegisters(operands);
            case INT ->
                validateInterrupt(operands);
            case JMP, JE, JNE ->
                validateJump(operands);
            case PARAM ->
                validateParams(operands);
        }
    }

    private static void validateMOV(List<String> ops) {
        if (ops.size() != 2) {
            throw new IllegalArgumentException("MOV requiere dos operandos.");
        }

        // Primer operando debe ser registro
        validateRegister(ops.get(0));

        // Segundo puede ser registro o número inmediato
        String src = ops.get(1);
        if (!isRegister(src) && !src.matches("-?\\d+")) {
            throw new IllegalArgumentException("Operando fuente inválido: " + src);
        }
    }

    private static void validateSingleRegister(List<String> ops) {
        if (ops.size() != 1) {
            throw new IllegalArgumentException("Se esperaba un solo registro.");
        }
        validateRegister(ops.get(0));
    }

    private static void validateTwoRegisters(List<String> ops) {
        if (ops.size() != 2) {
            throw new IllegalArgumentException("Se esperaban dos registros.");
        }
        for (String op : ops) {
            validateRegister(op);
        }
    }

    private static void validateIncDec(CodigoOperacion opcode, List<String> ops) {
        if (ops.size() > 1) {
            throw new IllegalArgumentException(opcode + " solo acepta 0 o 1 operando.");
        }
        if (ops.size() == 1) {
            validateRegister(ops.get(0));
        }
    }

    private static void validateRegister(String op) {
        Registro.fromString(op); // Lanza excepción si no es válido
    }

    private static boolean isRegister(String op) {
        try {
            Registro.fromString(op);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void validateInterrupt(List<String> ops) {
        if (ops.size() != 1) {
            throw new IllegalArgumentException("INT requiere un solo operando.");
        }
        String code = ops.get(0).toUpperCase();
        if (!code.matches("0?9H|10H|20H|21H")) {
            throw new IllegalArgumentException("Interrupción no válida: " + code);
        }
        // Ajustar peso según el tipo de interrupción
        int weight;
        switch (code) {
            case "09H" ->
                weight = 3;
            case "10H" ->
                weight = 2;
            case "20H" ->
                weight = 2;
            case "21H" ->
                weight = 5;
            default ->
                weight = -1;
        }
        CodigoOperacion.INT.setPeso(weight);
    }

    private static void validateJump(List<String> ops) {
        if (ops.size() != 1 || !ops.get(0).matches("[+-]?\\d+")) {
            throw new IllegalArgumentException("Salto inválido: " + ops);
        }
    }

    private static void validateParams(List<String> ops) {
        if (ops.size() < 1 || ops.size() > 3) {
            throw new IllegalArgumentException("PARAM acepta de 1 a 3 parámetros.");
        }
        for (String op : ops) {
            if (!op.matches("-?\\d+")) {
                throw new IllegalArgumentException("Parámetro no numérico: " + op);
            }
        }
    }    
}
