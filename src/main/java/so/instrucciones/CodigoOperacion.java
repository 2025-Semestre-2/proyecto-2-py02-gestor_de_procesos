package so.instrucciones;

/**
 *
 * @author dylan
 */
public enum CodigoOperacion {
    LOAD(2, 1),
    STORE(2, 1),
    MOV(1, 2),
    ADD(3, 1),
    SUB(3, 1),
    INC(1, -1), // puede tener 0 o 1 operando
    DEC(1, -1),
    SWAP(1, 2),
    INT(-1, 1), // el peso depende del tipo de interrupción
    JMP(2, 1),
    CMP(2, 2),
    JE(2, 1),
    JNE(2, 1),
    PARAM(3, -1), // de 1 a 3 operandos
    PUSH(1, 1),
    POP(1, 1);

    private int peso;
    private final int operandos;

    CodigoOperacion(int peso, int operandos) {
        this.peso = peso;
        this.operandos = operandos;
    }

    public int getPeso() {
        return peso;
    }

    public int getOperandos() {
        return operandos;
    }
    
    public void setPeso(int peso) {
        this.peso = peso;
    }

    public static CodigoOperacion fromString(String s) {
        s = s.trim().toUpperCase();
        return switch (s) {
            case "LOAD" ->
                LOAD;
            case "STORE" ->
                STORE;
            case "MOV" ->
                MOV;
            case "ADD" ->
                ADD;
            case "SUB" ->
                SUB;
            case "INC" ->
                INC;
            case "DEC" ->
                DEC;
            case "SWAP" ->
                SWAP;
            case "INT" ->
                INT;
            case "JMP" ->
                JMP;
            case "CMP" ->
                CMP;
            case "JE" ->
                JE;
            case "JNE" ->
                JNE;
            case "PARAM" ->
                PARAM;
            case "PUSH" ->
                PUSH;
            case "POP" ->
                POP;
            default ->
                throw new IllegalArgumentException("Instrucción desconocida: " + s);
        };
    }    
}
