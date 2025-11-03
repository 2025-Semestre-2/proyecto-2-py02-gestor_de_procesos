package so.cpu;

import so.instrucciones.Instruccion;
import so.gestordeprocesos.BCP;

/**
 * Simula el CPU del sistema operativo.
 * 
 * Responsabilidades:
 * - Mantener los registros del procesador
 * - Mantener el Instruction Register (IR) - instrucción actual
 * - Mantener el Program Counter (PC) - dirección de siguiente instrucción
 * - Ejecutar ciclos de fetch-decode
 * - Proporcionar acceso a registros para la ejecución de instrucciones
 * 
 * Nota: La ejecución real de instrucciones se delega a EjecutorInstrucciones
 * El CPU solo mantiene el estado del procesador
 * 
 * @author dylan
 */
public class CPU {
    
    // ========== REGISTROS DEL CPU ==========
    
    /**
     * Registro de instrucción (IR)
     * Almacena la instrucción que se está ejecutando actualmente
     */
    private Instruccion IR;
    
    /**
     * Contador de programa (Program Counter)
     * Apunta a la siguiente instrucción a ejecutar
     * Se actualiza después de cada instrucción
     */
    private int PC;
    
    /**
     * Acumulador
     * Registro general para operaciones aritméticas
     */
    private int AC;
    
    /**
     * Registros de propósito general
     */
    private int AX;
    private int BX;
    private int CX;
    private int DX;
    
    /**
     * Registro de estado (flags)
     * Almacena el resultado de la última comparación
     * -1: menor, 0: igual, 1: mayor
     */
    private int flagComparacion;
    
    /**
     * Número de ciclos de reloj ejecutados
     * Útil para estadísticas y debugging
     */
    private long ciclosReloj;
    
    /**
     * Número de instrucciones ejecutadas
     */
    private long instruccionesEjecutadas;
    
    /**
     * Constructor del CPU
     * Inicializa todos los registros en 0
     */
    public CPU() {
        reiniciar();
    }
    
    /**
     * Reinicia el CPU a su estado inicial
     */
    public void reiniciar() {
        this.IR = null;
        this.PC = 0;
        this.AC = 0;
        this.AX = 0;
        this.BX = 0;
        this.CX = 0;
        this.DX = 0;
        this.flagComparacion = 0;
        this.ciclosReloj = 0;
        this.instruccionesEjecutadas = 0;
    }
    
    // ========== FETCH-DECODE (CICLO DE CPU) ==========
    
    /**
     * Simula un ciclo de fetch
     * Carga la instrucción del BCP actual
     * 
     * @param bcp BCP con la información del proceso
     */
    public void fetch(BCP bcp) {
        if (bcp == null) {
            return;
        }
        
        // El IR se obtiene del BCP (ya fue cargado por EjecutorInstrucciones)
        this.IR = bcp.getIR();
        this.PC = bcp.getPC();
        
        ciclosReloj++;
    }
    
    /**
     * Simula un ciclo de decode
     * Valida que la instrucción cargada sea válida
     * 
     * @return true si la instrucción es válida, false si hay error
     */
    public boolean decode() {
        if (IR == null) {
            System.err.println("[CPU] Error: No hay instrucción en IR");
            return false;
        }
        
        if (IR.getCodigoOperacion() == null) {
            System.err.println("[CPU] Error: Instrucción inválida");
            return false;
        }
        
        ciclosReloj++;
        return true;
    }
    
    /**
     * Incrementa el contador de instrucciones ejecutadas
     */
    public void incrementarInstrucciones() {
        instruccionesEjecutadas++;
    }
    
    /**
     * Incrementa el contador de ciclos de reloj
     */
    public void incrementarCiclos(int cantidad) {
        ciclosReloj += cantidad;
    }
    
    // ========== GESTIÓN DE REGISTROS ==========
    
    /**
     * Obtiene el valor de un registro
     * 
     * @param nombreRegistro nombre del registro (AC, AX, BX, CX, DX)
     * @return valor del registro
     */
    public int obtenerRegistro(String nombreRegistro) {
        return switch (nombreRegistro.toUpperCase()) {
            case "AC" -> AC;
            case "AX" -> AX;
            case "BX" -> BX;
            case "CX" -> CX;
            case "DX" -> DX;
            default -> throw new IllegalArgumentException("Registro desconocido: " + nombreRegistro);
        };
    }
    
    /**
     * Establece el valor de un registro
     * 
     * @param nombreRegistro nombre del registro
     * @param valor valor a establecer
     */
    public void establecerRegistro(String nombreRegistro, int valor) {
        switch (nombreRegistro.toUpperCase()) {
            case "AC" -> AC = valor;
            case "AX" -> AX = valor;
            case "BX" -> BX = valor;
            case "CX" -> CX = valor;
            case "DX" -> DX = valor;
            default -> throw new IllegalArgumentException("Registro desconocido: " + nombreRegistro);
        }
    }
    
    /**
     * Copia los registros del CPU al BCP
     * Usado cuando se pausa/cambia de contexto
     * 
     * @param bcp BCP donde se copian los registros
     */
    public void guardarContexto(BCP bcp) {
        if (bcp == null) return;
        
        bcp.setAC(AC);
        bcp.setAX(AX);
        bcp.setBX(BX);
        bcp.setCX(CX);
        bcp.setDX(DX);
        bcp.setPC(PC);
        bcp.setIR(IR);
        bcp.setFlagComparacion(flagComparacion);
    }
    
    /**
     * Copia los registros del BCP al CPU
     * Usado cuando se despacha un proceso
     * 
     * @param bcp BCP desde donde se cargan los registros
     */
    public void cargarContexto(BCP bcp) {
        if (bcp == null) return;
        
        AC = bcp.getAC();
        AX = bcp.getAX();
        BX = bcp.getBX();
        CX = bcp.getCX();
        DX = bcp.getDX();
        PC = bcp.getPC();
        IR = bcp.getIR();
        flagComparacion = bcp.getFlagComparacion();
    }
    
    // ========== OPERACIONES ARITMÉTICAS ==========
    
    /**
     * Suma dos registros
     * 
     * @param reg1 primer registro
     * @param reg2 segundo registro
     * @return resultado de la suma
     */
    public int sumar(String reg1, String reg2) {
        return obtenerRegistro(reg1) + obtenerRegistro(reg2);
    }
    
    /**
     * Resta dos registros
     * 
     * @param reg1 primer registro (minuendo)
     * @param reg2 segundo registro (sustraendo)
     * @return resultado de la resta
     */
    public int restar(String reg1, String reg2) {
        return obtenerRegistro(reg1) - obtenerRegistro(reg2);
    }
    
    /**
     * Compara dos registros
     * Establece el flag de comparación
     * 
     * @param reg1 primer registro
     * @param reg2 segundo registro
     */
    public void comparar(String reg1, String reg2) {
        int valor1 = obtenerRegistro(reg1);
        int valor2 = obtenerRegistro(reg2);
        
        if (valor1 < valor2) {
            flagComparacion = -1;
        } else if (valor1 == valor2) {
            flagComparacion = 0;
        } else {
            flagComparacion = 1;
        }
    }
    
    // ========== OPERACIONES DE BITS ==========
    
    /**
     * Incrementa el AC
     */
    public void incrementarAC() {
        AC++;
    }
    
    /**
     * Decrementa el AC
     */
    public void decrementarAC() {
        AC--;
    }
    
    /**
     * Incrementa un registro
     * 
     * @param registro nombre del registro
     */
    public void incrementar(String registro) {
        int valor = obtenerRegistro(registro);
        establecerRegistro(registro, valor + 1);
    }
    
    /**
     * Decrementa un registro
     * 
     * @param registro nombre del registro
     */
    public void decrementar(String registro) {
        int valor = obtenerRegistro(registro);
        establecerRegistro(registro, valor - 1);
    }
    
    // ========== INFORMACIÓN Y ESTADO ==========
    
    /**
     * Obtiene el estado actual del CPU
     * 
     * @return string con información del CPU
     */
    public String getEstado() {
        return String.format(
            "CPU State:\n" +
            "  PC: %d\n" +
            "  AC: %d\n" +
            "  AX: %d\n" +
            "  BX: %d\n" +
            "  CX: %d\n" +
            "  DX: %d\n" +
            "  Flag: %d\n" +
            "  IR: %s\n" +
            "  Ciclos: %d\n" +
            "  Instrucciones: %d",
            PC, AC, AX, BX, CX, DX, flagComparacion,
            (IR != null ? IR.toString() : "null"),
            ciclosReloj, instruccionesEjecutadas
        );
    }
    
    /**
     * Genera un reporte del CPU para visualización
     */
    public String generarReporte() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== ESTADO DEL CPU ==========\n");
        sb.append(String.format("Instrucción Actual (IR): %s\n", 
                 IR != null ? IR.toString() : "Ninguna"));
        sb.append(String.format("Program Counter (PC): %d\n", PC));
        sb.append("\nRegistros:\n");
        sb.append(String.format("  AC (Acumulador): %d\n", AC));
        sb.append(String.format("  AX: %d\n", AX));
        sb.append(String.format("  BX: %d\n", BX));
        sb.append(String.format("  CX: %d\n", CX));
        sb.append(String.format("  DX: %d\n", DX));
        sb.append(String.format("\nFlag de Comparación: %d (%s)\n", 
                 flagComparacion,
                 getFlagComparacionTexto()));
        sb.append(String.format("Ciclos de Reloj: %d\n", ciclosReloj));
        sb.append(String.format("Instrucciones Ejecutadas: %d\n", instruccionesEjecutadas));
        sb.append("====================================\n");
        
        return sb.toString();
    }
    
    /**
     * Obtiene una descripción textual del flag de comparación
     */
    private String getFlagComparacionTexto() {
        return switch (flagComparacion) {
            case -1 -> "Menor";
            case 0 -> "Igual";
            case 1 -> "Mayor";
            default -> "No definido";
        };
    }
    
    // ========== GETTERS Y SETTERS ==========
    
    public Instruccion getIR() {
        return IR;
    }

    public void setIR(Instruccion IR) {
        this.IR = IR;
    }

    public int getPC() {
        return PC;
    }

    public void setPC(int PC) {
        this.PC = PC;
    }

    public int getAC() {
        return AC;
    }

    public void setAC(int AC) {
        this.AC = AC;
    }

    public int getAX() {
        return AX;
    }

    public void setAX(int AX) {
        this.AX = AX;
    }

    public int getBX() {
        return BX;
    }

    public void setBX(int BX) {
        this.BX = BX;
    }

    public int getCX() {
        return CX;
    }

    public void setCX(int CX) {
        this.CX = CX;
    }

    public int getDX() {
        return DX;
    }

    public void setDX(int DX) {
        this.DX = DX;
    }

    public int getFlagComparacion() {
        return flagComparacion;
    }

    public void setFlagComparacion(int flagComparacion) {
        this.flagComparacion = flagComparacion;
    }

    public long getCiclosReloj() {
        return ciclosReloj;
    }

    public long getInstruccionesEjecutadas() {
        return instruccionesEjecutadas;
    }
    
    @Override
    public String toString() {
        return String.format("CPU[PC=%d, AC=%d, AX=%d, BX=%d, CX=%d, DX=%d, Flag=%d]",
                           PC, AC, AX, BX, CX, DX, flagComparacion);
    }
}