package so.gestordeprocesos;

import so.instrucciones.Instruccion;
import java.util.ArrayList;
import java.util.List;

/**
 * Bloque de Control de Procesos (BCP) - MODIFICADO para Particionamiento Fijo.
 * 
 * Atributos adicionales para particionamiento:
 * - indiceParticion: índice de la partición asignada al proceso
 * - tamanoParticion: tamaño de la partición asignada
 * - fragmentacionInterna: espacio desperdiciado en la partición
 * 
 * Estructura de 28 atributos (original: 25, nuevos: 3)
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
    
    // ========== ATRIBUTOS ADICIONALES PARA PARTICIONAMIENTO FIJO ==========
    private int indiceParticion;        // Índice de partición asignada (0-22)
    private int tamanoParticion;        // Tamaño de la partición asignada
    private int fragmentacionInterna;   // Espacio desperdiciado (tamanoParticion - tamanoProceso)
    
    // ========== ATRIBUTOS ADICIONALES PARA PARTICIONAMIENTO DINÁMICO (BUDDY SYSTEM) ==========
    private int indiceBloqueMemoria;    // Índice del bloque en la lista de bloques libres/ocupados
    private int tamanoBloqueAsignado;   // Tamaño real del bloque asignado (potencia de 2)
    private int nivelBuddy;             // Nivel en el árbol buddy (0 = bloque más grande)
    private int direccionBloque;        // Dirección de inicio del bloque en memoria
    
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
        
        // Inicializar atributos de particionamiento
        this.indiceParticion = -1;
        this.tamanoParticion = 0;
        this.fragmentacionInterna = 0;
        
        // Inicializar atributos de buddy system
        this.indiceBloqueMemoria = -1;
        this.tamanoBloqueAsignado = 0;
        this.nivelBuddy = -1;
        this.direccionBloque = -1;      
    }
    
    /**
     * Constructor vacío para deserialización desde memoria
     */
    public BCP() {
        this.pila = new int[5];
        this.archivosAbiertos = new ArrayList<>();
        this.indiceParticion = -1;
        this.tamanoParticion = 0;
        this.fragmentacionInterna = 0;
        this.indiceBloqueMemoria = -1;
        this.tamanoBloqueAsignado = 0;
        this.nivelBuddy = -1;
        this.direccionBloque = -1;
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
    
    // ========== MÉTODOS ESPECÍFICOS DE PARTICIONAMIENTO FIJO ==========
    
    /**
     * Asigna una partición al proceso
     */
    public void asignarParticion(int indiceParticion, int tamanoParticion) {
        this.indiceParticion = indiceParticion;
        this.tamanoParticion = tamanoParticion;
        this.fragmentacionInterna = tamanoParticion - this.tamanoProceso;
        
        if (this.fragmentacionInterna < 0) {
            this.fragmentacionInterna = 0;
        }
    }
    
    /**
     * Libera la partición asignada
     */
    public void liberarParticion() {
        this.indiceParticion = -1;
        this.tamanoParticion = 0;
        this.fragmentacionInterna = 0;
    }
    
    /**
     * Verifica si el proceso tiene una partición asignada
     */
    public boolean tieneParticionAsignada() {
        return indiceParticion >= 0;
    }
    
    /**
     * Calcula el porcentaje de uso de la partición
     */
    public double getPorcentajeUsoParticion() {
        if (tamanoParticion == 0) return 0.0;
        return (double) this.tamanoProceso / tamanoParticion * 100.0;
    }
    
    /**
     * Obtiene información sobre la partición asignada
     */
    public String getInfoParticion() {
        if (!tieneParticionAsignada()) {
            return "Sin partición asignada";
        }
        
        return String.format(
            "Partición %d: %d KB asignados, %d KB usados, %.1f%% uso, %d KB desperdiciados",
            indiceParticion,
            tamanoParticion,
            this.tamanoProceso,
            getPorcentajeUsoParticion(),
            fragmentacionInterna
        );
    }
    
    // ========== MÉTODOS ESPECÍFICOS DE PARTICIONAMIENTO DINÁMICO (BUDDY SYSTEM) ==========
    
    /**
     * Asigna un bloque buddy al proceso
     */
    public void asignarBloqueBuddy(int indiceBloque, int tamanoBloque, int nivel, int direccion) {
        this.indiceBloqueMemoria = indiceBloque;
        this.tamanoBloqueAsignado = tamanoBloque;
        this.nivelBuddy = nivel;
        this.direccionBloque = direccion;
        
        // Calcular fragmentación interna para buddy system
        this.fragmentacionInterna = tamanoBloque - this.tamanoProceso;
        if (this.fragmentacionInterna < 0) {
            this.fragmentacionInterna = 0;
        }
    }
    
    /**
     * Libera el bloque buddy asignado
     */
    public void liberarBloqueBuddy() {
        this.indiceBloqueMemoria = -1;
        this.tamanoBloqueAsignado = 0;
        this.nivelBuddy = -1;
        this.direccionBloque = -1;
    }
    
    /**
     * Verifica si el proceso tiene un bloque buddy asignado
     */
    public boolean tieneBloqueBuddyAsignado() {
        return indiceBloqueMemoria >= 0;
    }
    
    /**
     * Calcula el porcentaje de uso del bloque buddy
     */
    public double getPorcentajeUsoBuddy() {
        if (tamanoBloqueAsignado == 0) return 0.0;
        return (double) this.tamanoProceso / tamanoBloqueAsignado * 100.0;
    }
    
    /**
     * Obtiene información sobre el bloque buddy asignado
     */
    public String getInfoBloqueBuddy() {
        if (!tieneBloqueBuddyAsignado()) {
            return "Sin bloque buddy asignado";
        }
        
        return String.format(
            "Bloque %d (Nivel %d): %d KB asignados, %d KB usados, %.1f%% uso, %d KB desperdiciados, Dir: %d",
            indiceBloqueMemoria,
            nivelBuddy,
            tamanoBloqueAsignado,
            this.tamanoProceso,
            getPorcentajeUsoBuddy(),
            fragmentacionInterna,
            direccionBloque
        );
    }
   
    // ========== SERIALIZACIÓN ==========
    
    
    /**
     * Guarda el BCP en memoria principal (36 atributos: 25 originales + 3 fijo + 4 buddy + 4 segmentación)
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
        
        // Atributos de particionamiento fijo (posiciones 25, 26, 27)
        memoria[indiceInicio + 25] = indiceParticion;
        memoria[indiceInicio + 26] = tamanoParticion;
        memoria[indiceInicio + 27] = fragmentacionInterna;
        
        // Atributos de buddy system (posiciones 28, 29, 30, 31)
        memoria[indiceInicio + 28] = indiceBloqueMemoria;
        memoria[indiceInicio + 29] = tamanoBloqueAsignado;
        memoria[indiceInicio + 30] = nivelBuddy;
        memoria[indiceInicio + 31] = direccionBloque;       
    }
    
    /**
     * Carga un BCP desde memoria principal (36 atributos)
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
        
        // Cargar los 3 atributos de particionamiento fijo
        bcp.indiceParticion = (Integer) memoria[indiceInicio + 25];
        bcp.tamanoParticion = (Integer) memoria[indiceInicio + 26];
        bcp.fragmentacionInterna = (Integer) memoria[indiceInicio + 27];
        
        // Cargar los 4 atributos de buddy system
        bcp.indiceBloqueMemoria = (Integer) memoria[indiceInicio + 28];
        bcp.tamanoBloqueAsignado = (Integer) memoria[indiceInicio + 29];
        bcp.nivelBuddy = (Integer) memoria[indiceInicio + 30];
        bcp.direccionBloque = (Integer) memoria[indiceInicio + 31];
             
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
    
    // ========== GETTERS Y SETTERS DE PARTICIONAMIENTO ==========
    public int getIndiceParticion() {
        return indiceParticion;
    }
    
    public void setIndiceParticion(int indiceParticion) {
        this.indiceParticion = indiceParticion;
    }
    
    public int getTamanoParticion() {
        return tamanoParticion;
    }
    
    public void setTamanoParticion(int tamanoParticion) {
        this.tamanoParticion = tamanoParticion;
    }
    
    public int getFragmentacionInterna() {
        return fragmentacionInterna;
    }
    
    public void setFragmentacionInterna(int fragmentacionInterna) {
        this.fragmentacionInterna = fragmentacionInterna;
    }

    public int getIndiceBloqueMemoria() {
        return indiceBloqueMemoria;
    }
    
    public void setIndiceBloqueMemoria(int indiceBloqueMemoria) {
        this.indiceBloqueMemoria = indiceBloqueMemoria;
    }

    public int getTamanoBloqueAsignado() {
        return tamanoBloqueAsignado;
    }
    
    public void setTamanoBloqueAsignado(int tamanoBloqueAsignado) {
        this.tamanoBloqueAsignado = tamanoBloqueAsignado;
    }    
    
    public int getNivelBuddy() {
        return nivelBuddy;
    }
    
    public void setNivelBuddy(int nivelBuddy) {
        this.nivelBuddy = nivelBuddy;
    }  

    public int getDireccionBloque() {
        return direccionBloque;
    }
    
    public void setDireccionBloque(int direccionBloque) {
        this.direccionBloque = direccionBloque;
    } 
    
    @Override
    public String toString() {
        return String.format(
            "BCP[id=%d, nombre=%s, estado=%s, PC=%d/%d, partición=%d(%dKB)]",
            getIdProceso(), 
            getNombreProceso(), 
            getEstado(), 
            getPC(), 
            getTamanoProceso(),
            indiceParticion,
            tamanoParticion
        );
    }
    
    /**
     * Genera un reporte detallado del BCP con información de particionamiento
     */
    public String generarReporteDetallado() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append(String.format("Proceso: %s (ID: %d)\n", getNombreProceso(), getIdProceso()));
        sb.append(String.format("Estado: %s\n", getEstado()));
        sb.append(String.format("PC: %d / %d (%.1f%%)\n", 
            getPC(), getTamanoProceso(), getProgreso()));
        sb.append(String.format("Dirección base: %d\n", getDireccionBase()));
        
        sb.append("\n--- Particionamiento ---\n");
        if (tieneParticionAsignada()) {
            sb.append(getInfoParticion()).append("\n");
        } else {
            sb.append("Sin partición asignada\n");
        }
        
        sb.append("\n--- Registros ---\n");
        sb.append(String.format("  AC: %d\n", getAC()));
        sb.append(String.format("  AX: %d\n", getAX()));
        sb.append(String.format("  BX: %d\n", getBX()));
        sb.append(String.format("  CX: %d\n", getCX()));
        sb.append(String.format("  DX: %d\n", getDX()));
        
        sb.append("\n--- Tiempos ---\n");
        sb.append(String.format("Tiempo CPU usado: %d seg\n", getTiempoCPUUsado()));
        sb.append(String.format("Tiempo espera: %d seg\n", getTiempoEspera()));
        
        sb.append("========================================\n");
        return sb.toString();
    }
}