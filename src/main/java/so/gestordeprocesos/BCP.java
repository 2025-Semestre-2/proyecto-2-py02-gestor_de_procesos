package so.gestordeprocesos;

import so.instrucciones.Instruccion;
import java.util.ArrayList;
import java.util.List;

/**
 * Bloque de Control de Procesos (BCP).
 * Contiene toda la información necesaria para gestionar un proceso.
 * 
 * Estructura de 25 atributos que se serializan en memoria principal.
 * 
 * @author dylan
 */
public class BCP {
    
    // ========== IDENTIFICACIÓN ==========
    private int idProceso;
    private String nombreProceso;
    
    // ========== ESTADO Y CONTROL ==========
    private EstadoProceso estado;
    private int PC; // Program Counter
    private int direccionBase; // inicio en memoria principal
    private int tamanoProceso; // número de instrucciones
    private int rafaga; // igual a tamanoProceso (para planificadores)
    
    // ========== REGISTROS DEL CPU ==========
    private int AC; // Acumulador
    private int AX;
    private int BX;
    private int CX;
    private int DX;
    private Instruccion IR; // Instruction Register (instrucción actual)
    
    // ========== PILA ==========
    private int stackPointer; // índice actual en la pila (0-5)
    private int[] pila; // tamaño fijo de 5
    
    // ========== PLANIFICACIÓN ==========
    private int prioridad;
    private long tiempoInicio; // timestamp en milisegundos
    private int tiempoCPUUsado; // segundos acumulados
    private int tiempoEspera; // para HRRN
    private int quantumRestante; // para Round Robin
    
    // ========== CONTROL DE FLUJO ==========
    private int flagComparacion; // -1: menor, 0: igual, 1: mayor (para CMP)
    
    // ========== OTROS ==========
    private List<String> archivosAbiertos; // para futura expansión INT 21H
    
    /**
     * Constructor completo para crear un nuevo BCP
     */
    public BCP(int id, String nombre, int direccionBase, int tamanoProceso) {
        this.idProceso = id;
        this.nombreProceso = nombre;
        this.direccionBase = direccionBase;
        this.tamanoProceso = tamanoProceso;
        this.rafaga = tamanoProceso;
        
        // Inicializar estado y control
        this.estado = EstadoProceso.NUEVO;
        this.PC = 0;
        
        // Inicializar registros en 0
        this.AC = 0;
        this.AX = 0;
        this.BX = 0;
        this.CX = 0;
        this.DX = 0;
        this.IR = null;
        
        // Inicializar pila
        this.stackPointer = 0;
        this.pila = new int[5];
        
        // Inicializar planificación
        this.prioridad = 0;
        this.tiempoInicio = System.currentTimeMillis();
        this.tiempoCPUUsado = 0;
        this.tiempoEspera = 0;
        this.quantumRestante = 0;
        
        // Control de flujo
        this.flagComparacion = 0;
        
        // Otros
        this.archivosAbiertos = new ArrayList<>();
    }
    
    /**
     * Constructor vacío para deserialización desde memoria
     */
    public BCP() {
        this.pila = new int[5];
        this.archivosAbiertos = new ArrayList<>();
    }
    
    // ========== GESTIÓN DE PILA ==========
    
    /**
     * Agrega un valor a la pila
     * @param valor valor a agregar
     */
    public void push(int valor) {
        if (stackPointer >= 5) {
            throw new RuntimeException("Desbordamiento de pila en proceso " + nombreProceso);
        }
        pila[stackPointer++] = valor;
    }
    
    /**
     * Extrae un valor de la pila
     * @return valor extraído
     */
    public int pop() {
        if (stackPointer <= 0) {
            throw new RuntimeException("Pila vacía en proceso " + nombreProceso);
        }
        return pila[--stackPointer];
    }
    
    /**
     * Verifica si la pila está vacía
     */
    public boolean pilaVacia() {
        return stackPointer == 0;
    }
    
    /**
     * Verifica si la pila está llena
     */
    public boolean pilaLlena() {
        return stackPointer >= 5;
    }
    
    // ========== GESTIÓN DE TIEMPO ==========
    
    /**
     * Incrementa el tiempo de CPU usado en 1 segundo
     */
    public void incrementarTiempoCPU() {
        this.tiempoCPUUsado++;
    }
    
    /**
     * Incrementa el tiempo de espera en 1 segundo
     */
    public void incrementarTiempoEspera() {
        this.tiempoEspera++;
    }
    
    /**
     * Decrementa el quantum restante (para Round Robin)
     */
    public void decrementarQuantum() {
        if (this.quantumRestante > 0) {
            this.quantumRestante--;
        }
    }
    
    /**
     * Reinicia el quantum (para Round Robin)
     */
    public void reiniciarQuantum(int quantum) {
        this.quantumRestante = quantum;
    }
    
    // ========== CONTROL DE EJECUCIÓN ==========
    
    /**
     * Verifica si el proceso ha terminado de ejecutarse
     */
    public boolean haTerminado() {
        return PC >= tamanoProceso || estado == EstadoProceso.FINALIZADO;
    }
    
    /**
     * Obtiene el número de instrucciones restantes
     */
    public int getInstruccionesRestantes() {
        return tamanoProceso - PC;
    }
    
    /**
     * Obtiene el progreso del proceso (porcentaje)
     */
    public double getProgreso() {
        if (tamanoProceso == 0) return 0;
        return (PC * 100.0) / tamanoProceso;
    }
    
    /**
     * Obtiene el valor de un registro por nombre
     */
    public int getRegistro(String nombre) {
        return switch (nombre.toUpperCase()) {
            case "AC" -> AC;
            case "AX" -> AX;
            case "BX" -> BX;
            case "CX" -> CX;
            case "DX" -> DX;
            default -> throw new IllegalArgumentException("Registro desconocido: " + nombre);
        };
    }
    
    /**
     * Establece el valor de un registro por nombre
     */
    public void setRegistro(String nombre, int valor) {
        switch (nombre.toUpperCase()) {
            case "AC" -> AC = valor;
            case "AX" -> AX = valor;
            case "BX" -> BX = valor;
            case "CX" -> CX = valor;
            case "DX" -> DX = valor;
            default -> throw new IllegalArgumentException("Registro desconocido: " + nombre);
        }
    }
    
    // ========== SERIALIZACIÓN ==========
    
    /**
     * Guarda el BCP en memoria principal
     * Cada atributo ocupa una posición en el array
     * 
     * @param memoria array de memoria principal
     * @param indiceInicio índice donde comienza este BCP
     */
    public void guardarEnMemoria(Object[] memoria, int indiceInicio) {
        memoria[indiceInicio + 0] = idProceso;
        memoria[indiceInicio + 1] = nombreProceso;
        memoria[indiceInicio + 2] = estado.name();
        memoria[indiceInicio + 3] = PC;
        memoria[indiceInicio + 4] = direccionBase;
        memoria[indiceInicio + 5] = tamanoProceso;
        memoria[indiceInicio + 6] = AC;
        memoria[indiceInicio + 7] = AX;
        memoria[indiceInicio + 8] = BX;
        memoria[indiceInicio + 9] = CX;
        memoria[indiceInicio + 10] = DX;
        memoria[indiceInicio + 11] = IR; // referencia al objeto Instruccion
        memoria[indiceInicio + 12] = stackPointer;
        memoria[indiceInicio + 13] = pila[0];
        memoria[indiceInicio + 14] = pila[1];
        memoria[indiceInicio + 15] = pila[2];
        memoria[indiceInicio + 16] = pila[3];
        memoria[indiceInicio + 17] = pila[4];
        memoria[indiceInicio + 18] = prioridad;
        memoria[indiceInicio + 19] = tiempoInicio;
        memoria[indiceInicio + 20] = tiempoCPUUsado;
        memoria[indiceInicio + 21] = flagComparacion;
        memoria[indiceInicio + 22] = tiempoEspera;
        memoria[indiceInicio + 23] = quantumRestante;
        memoria[indiceInicio + 24] = archivosAbiertos;
    }
    
    /**
     * Carga un BCP desde memoria principal
     * 
     * @param memoria array de memoria principal
     * @param indiceInicio índice donde comienza este BCP
     * @return BCP reconstruido
     */
    public static BCP cargarDesdeMemoria(Object[] memoria, int indiceInicio) {
        BCP bcp = new BCP();
        
        bcp.idProceso = (Integer) memoria[indiceInicio + 0];
        bcp.nombreProceso = (String) memoria[indiceInicio + 1];
        bcp.estado = EstadoProceso.valueOf((String) memoria[indiceInicio + 2]);
        bcp.PC = (Integer) memoria[indiceInicio + 3];
        bcp.direccionBase = (Integer) memoria[indiceInicio + 4];
        bcp.tamanoProceso = (Integer) memoria[indiceInicio + 5];
        bcp.rafaga = bcp.tamanoProceso;
        bcp.AC = (Integer) memoria[indiceInicio + 6];
        bcp.AX = (Integer) memoria[indiceInicio + 7];
        bcp.BX = (Integer) memoria[indiceInicio + 8];
        bcp.CX = (Integer) memoria[indiceInicio + 9];
        bcp.DX = (Integer) memoria[indiceInicio + 10];
        bcp.IR = (Instruccion) memoria[indiceInicio + 11];
        bcp.stackPointer = (Integer) memoria[indiceInicio + 12];
        bcp.pila[0] = (Integer) memoria[indiceInicio + 13];
        bcp.pila[1] = (Integer) memoria[indiceInicio + 14];
        bcp.pila[2] = (Integer) memoria[indiceInicio + 15];
        bcp.pila[3] = (Integer) memoria[indiceInicio + 16];
        bcp.pila[4] = (Integer) memoria[indiceInicio + 17];
        bcp.prioridad = (Integer) memoria[indiceInicio + 18];
        bcp.tiempoInicio = (Long) memoria[indiceInicio + 19];
        bcp.tiempoCPUUsado = (Integer) memoria[indiceInicio + 20];
        bcp.flagComparacion = (Integer) memoria[indiceInicio + 21];
        bcp.tiempoEspera = (Integer) memoria[indiceInicio + 22];
        bcp.quantumRestante = (Integer) memoria[indiceInicio + 23];
        bcp.archivosAbiertos = (List<String>) memoria[indiceInicio + 24];
        
        return bcp;
    }
    
    // ========== GETTERS Y SETTERS ==========
    
    public int getIdProceso() {
        return idProceso;
    }

    public void setIdProceso(int idProceso) {
        this.idProceso = idProceso;
    }

    public String getNombreProceso() {
        return nombreProceso;
    }

    public void setNombreProceso(String nombreProceso) {
        this.nombreProceso = nombreProceso;
    }

    public EstadoProceso getEstado() {
        return estado;
    }

    public void setEstado(EstadoProceso estado) {
        this.estado = estado;
    }

    public int getPC() {
        return PC;
    }

    public void setPC(int PC) {
        this.PC = PC;
    }

    public int getDireccionBase() {
        return direccionBase;
    }

    public void setDireccionBase(int direccionBase) {
        this.direccionBase = direccionBase;
    }

    public int getTamanoProceso() {
        return tamanoProceso;
    }

    public void setTamanoProceso(int tamanoProceso) {
        this.tamanoProceso = tamanoProceso;
    }

    public int getRafaga() {
        return rafaga;
    }

    public void setRafaga(int rafaga) {
        this.rafaga = rafaga;
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

    public Instruccion getIR() {
        return IR;
    }

    public void setIR(Instruccion IR) {
        this.IR = IR;
    }

    public int getStackPointer() {
        return stackPointer;
    }

    public void setStackPointer(int stackPointer) {
        this.stackPointer = stackPointer;
    }

    public int[] getPila() {
        return pila;
    }

    public void setPila(int[] pila) {
        this.pila = pila;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(int prioridad) {
        this.prioridad = prioridad;
    }

    public long getTiempoInicio() {
        return tiempoInicio;
    }

    public void setTiempoInicio(long tiempoInicio) {
        this.tiempoInicio = tiempoInicio;
    }

    public int getTiempoCPUUsado() {
        return tiempoCPUUsado;
    }

    public void setTiempoCPUUsado(int tiempoCPUUsado) {
        this.tiempoCPUUsado = tiempoCPUUsado;
    }

    public int getTiempoEspera() {
        return tiempoEspera;
    }

    public void setTiempoEspera(int tiempoEspera) {
        this.tiempoEspera = tiempoEspera;
    }

    public int getQuantumRestante() {
        return quantumRestante;
    }

    public void setQuantumRestante(int quantumRestante) {
        this.quantumRestante = quantumRestante;
    }

    public int getFlagComparacion() {
        return flagComparacion;
    }

    public void setFlagComparacion(int flagComparacion) {
        this.flagComparacion = flagComparacion;
    }

    public List<String> getArchivosAbiertos() {
        return archivosAbiertos;
    }

    public void setArchivosAbiertos(List<String> archivosAbiertos) {
        this.archivosAbiertos = archivosAbiertos;
    }
    
    @Override
    public String toString() {
        return String.format("BCP[id=%d, nombre=%s, estado=%s, PC=%d/%d, base=%d]",
                idProceso, nombreProceso, estado, PC, tamanoProceso, direccionBase);
    }
}