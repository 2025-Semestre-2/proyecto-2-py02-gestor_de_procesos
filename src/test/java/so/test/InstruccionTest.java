package so.test;

import so.instrucciones.*;

/**
 *
 * @author dylan
 */
public class InstruccionTest {

    public static void main(String[] args) {
        String[] program = {
            // --- Casos válidos ---
            "LOAD AX",
            "STORE BX",
            "MOV BX, AX",
            "MOV BX, 5",
            "ADD BX",
            "SUB BX",
            "INC",
            "DEC AX",
            "SWAP AX, BX",
            "CMP AX, BX",
            "JMP -2",
            "JE 3",
            "JNE -4",
            "INT 20H",
            "INT 10H",            
            "INT 09H",
            "INT 21H",            
            "PARAM 3,4,5",
            "PUSH AX",
            "POP BX",
            "", // línea vacía
            "; comentario",
            "LOOP:", // etiqueta (debe ignorarse)

            // --- Casos inválidos ---
            "LOAD", // falta operando
            "STORE", // falta operando
            "MOV BX", // faltan operandos
            "MOV", // faltan operandos
            "MOV BX, CX, DX", // demasiados operandos
            "MOV 5, BX", // orden incorrecto: inmediato primero
            "ADD", // falta operando
            "ADD 123", // operando inmediato donde se espera registro
            "SUB 4", // operando inmediato inválido
            "SWAP AX", // falta un registro
            "SWAP 5, AX", // operando inmediato inválido
            "INC BX, AX", // demasiados operandos
            "DEC 12, BX", // demasiados operandos e inválido
            "INT", // falta código
            "INT 15H", // interrupción no válida
            "JMP", // falta desplazamiento
            "JMP AX", // desplazamiento no numérico
            "JE X", // desplazamiento no numérico
            "JNE +A", // desplazamiento inválido
            "PARAM", // sin parámetros
            "PARAM 1,2,3,4", // demasiados parámetros
            "PARAM 1,A,3", // parámetro no numérico
            "PUSH", // falta registro
            "PUSH 5", // operando no es registro
            "POP", // falta registro
            "POP 10", // operando no es registro
            "FOO AX, BX", // instrucción desconocida
            "INT 999", // formato de interrupción inválido
            "MOV AY, BX", // registro inexistente
            "PARAM AX", // registro en lugar de número
            "JMP +", // desplazamiento incompleto
        };

        System.out.println("=== 🔍 PRUEBAS DE PARSEO DE INSTRUCCIONES ===\n");

        int lineNumber = 1;
        for (String line : program) {
            try {
                Instruccion instr = InstruccionParser.parse(line);
                if (instr != null) {
                    System.out.printf("%02d ✅ %s -> instruction:%s weight:%s%n", lineNumber, line, instr, instr.getCodigoOperacion().getPeso());
                } else {
                    System.out.printf("%02d ⚪ (ignorada) %s%n", lineNumber, line);
                }
            } catch (Exception e) {
                System.out.printf("%02d ❌ Error en '%s' -> %s%n", lineNumber, line, e.getMessage());
            }
            lineNumber++;
        }
    }
}
