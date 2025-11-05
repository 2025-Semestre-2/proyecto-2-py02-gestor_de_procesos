package so.memoria;

import so.instrucciones.Instruccion;
import so.gestordeprocesos.BCP;
import so.memoria.estrategias.IEstrategiaParticionamiento;
import so.memoria.estrategias.IEstrategiaParticionamiento.InfoAsignacion;

/**
 * Gestión de Memoria Principal unificada con soporte para múltiples estrategias.
 * Usa el patrón Strategy para permitir cambiar dinámicamente la estrategia de particionamiento.
 * 
 * Estructura de Memoria:
 * - [0-999]: Área del Sistema Operativo
 *   - [0-9]: Metadata general
 *   - [10-809]: 25 BCPs (32 atributos cada uno)
 *   - [810-949]: Espacio para colas y estructuras auxiliares
 *   - [950-999]: Reservado
 * - [1000-9999]: Área de Usuario (9000 KB gestionados por la estrategia)
 * 
 * @author dylan
 */
public class MemoriaPrincipalV2 {
    
    // ========== Variables de configuracion ===========
    private int tamanoTotal;
    private int tamanoSO;
    private int inicioUsuario;
    private int tamanoUsuario;    
    
    // ========== CONSTANTES DE CONFIGURACIÓN ==========
    private final int TAMANO_BCP = 32;
    private final int MAX_PROCESOS = 25;
    
    // ========== ÍNDICES EN MEMORIA - METADATA ==========
    private final int IDX_TAMANO_TOTAL = 0;
    private final int IDX_TAMANO_SO = 1;
    private final int IDX_TAMANO_USUARIO = 2;
    private final int IDX_BCPS_ACTIVOS = 3;
    private final int IDX_BCP_EN_EJECUCION = 4;
    private final int IDX_SIGUIENTE_ID = 5;
    private final int IDX_TIPO_ESTRATEGIA = 6;
    
    // ========== ÍNDICES - BCPs ==========
    private final int IDX_PRIMER_BCP = 10;
    
    // ========== ÍNDICES - COLAS ==========
    private final int IDX_COLA_TRABAJOS = 810;
    private final int IDX_TAMANO_COLA_TRABAJOS = 835;
    private final int IDX_COLA_LISTOS = 850;
    private final int IDX_TAMANO_COLA_LISTOS = 875;
    
    // ========== MEMORIA ==========
    private final Object[] memoria;
    
    // ========== ESTRATEGIA DE PARTICIONAMIENTO ==========
    private IEstrategiaParticionamiento estrategia;
    
    /**
     * Constructor con estrategia de particionamiento
     * 
     * @param estrategia estrategia de gestión de memoria a utilizar
     * @param tamanoUsuario
     */
    public MemoriaPrincipalV2(IEstrategiaParticionamiento estrategia, int tamanoUsuario) {
        this.tamanoSO = 1000; 
        this.tamanoUsuario = tamanoUsuario;
        this.tamanoTotal = tamanoSO + tamanoUsuario;
        this.inicioUsuario = tamanoSO;       
        
        this.memoria = new Object[tamanoTotal];
        this.estrategia = estrategia;
        
        inicializarMemoria();
        inicializarEstrategia();
    }
    
    /**
     * Inicializa la memoria con valores por defecto
     */
    private void inicializarMemoria() {
        // Limpiar toda la memoria
        for (int i = 0; i < tamanoTotal; i++) {
            memoria[i] = null;
        }
        
        // Inicializar metadata
        memoria[IDX_TAMANO_TOTAL] = tamanoTotal;
        memoria[IDX_TAMANO_SO] = tamanoSO;
        memoria[IDX_TAMANO_USUARIO] = tamanoUsuario;
        memoria[IDX_BCPS_ACTIVOS] = 0;
        memoria[IDX_BCP_EN_EJECUCION] = -1;
        memoria[IDX_SIGUIENTE_ID] = 1;
        memoria[IDX_TIPO_ESTRATEGIA] = estrategia.getNombre();
        
        // Inicializar colas
        memoria[IDX_TAMANO_COLA_TRABAJOS] = 0;
        memoria[IDX_TAMANO_COLA_LISTOS] = 0;
        
        System.out.println("[MEMORIA PRINCIPAL] Inicializada con estrategia: " + estrategia.getNombre());
    }
    
    /**
     * Inicializa la estrategia de particionamiento
     */
    private void inicializarEstrategia() {
        estrategia.inicializar(memoria, tamanoUsuario, inicioUsuario);
    }
    
    // ========== GESTIÓN DE INSTRUCCIONES ==========
    
    /**
     * Carga instrucciones en memoria usando la estrategia actual
     * 
     * @param instrucciones array de instrucciones a cargar
     * @return InfoAsignacion con datos de la asignación o null si no hay espacio
     */
    public InfoAsignacion cargarInstrucciones(Instruccion[] instrucciones) {
        return estrategia.cargarInstrucciones(instrucciones);
    }
    
    /**
     * Obtiene una instrucción de la memoria
     * 
     * @param direccion dirección de la instrucción
     * @return instrucción o null si no existe
     */
    public Instruccion obtenerInstruccion(int direccion) {
        if (direccion < inicioUsuario || direccion >= tamanoTotal) {
            return null;
        }
        
        Object obj = memoria[direccion];
        if (obj instanceof Instruccion) {
            return (Instruccion) obj;
        }
        
        return null;
    }
    
    // ========== GESTIÓN DE BCPs ==========
    
    /**
     * Busca un slot libre para un BCP
     * 
     * @return número de BCP (0-24) o -1 si no hay espacio
     */
    public int buscarBCPLibre() {
        for (int i = 0; i < MAX_PROCESOS; i++) {
            int indice = calcularIndiceBCP(i);
            if (memoria[indice] == null) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Crea un nuevo BCP en memoria
     * 
     * @param bcp BCP a crear
     * @return número de BCP asignado o -1 si no hay espacio
     */
    public int crearBCP(BCP bcp) {
        int numeroBCP = buscarBCPLibre();
        if (numeroBCP < 0) {
            return -1;
        }
        
        int indice = calcularIndiceBCP(numeroBCP);
        bcp.guardarEnMemoria(memoria, indice);
        
        int activos = getCantidadBCPsActivos();
        setCantidadBCPsActivos(activos + 1);
        
        return numeroBCP;
    }
    
    /**
     * Obtiene un BCP desde memoria
     * 
     * @param numeroBCP número del BCP (0-24)
     * @return BCP o null si no existe
     */
    public BCP obtenerBCP(int numeroBCP) {
        if (numeroBCP < 0 || numeroBCP >= MAX_PROCESOS) {
            return null;
        }
        
        int indice = calcularIndiceBCP(numeroBCP);
        if (memoria[indice] == null) {
            return null;
        }
        
        return BCP.cargarDesdeMemoria(memoria, indice);
    }
    
    /**
     * Actualiza un BCP en memoria
     * 
     * @param numeroBCP número del BCP
     * @param bcp BCP con datos actualizados
     */
    public void actualizarBCP(int numeroBCP, BCP bcp) {
        if (numeroBCP < 0 || numeroBCP >= MAX_PROCESOS) {
            throw new IllegalArgumentException("Número de BCP inválido: " + numeroBCP);
        }
        
        int indice = calcularIndiceBCP(numeroBCP);
        bcp.guardarEnMemoria(memoria, indice);
    }
    
    /**
     * Libera un BCP y su espacio de memoria asociado
     * 
     * @param numeroBCP número del BCP a liberar
     */
    public void liberarBCP(int numeroBCP) {
        if (numeroBCP < 0 || numeroBCP >= MAX_PROCESOS) {
            return;
        }
        
        BCP bcp = obtenerBCP(numeroBCP);
        if (bcp != null) {
            // Liberar espacio usando la estrategia
            estrategia.liberarEspacio(bcp);
        }
        
        // Limpiar BCP
        int indice = calcularIndiceBCP(numeroBCP);
        for (int i = 0; i < TAMANO_BCP; i++) {
            memoria[indice + i] = null;
        }
        
        int activos = getCantidadBCPsActivos();
        if (activos > 0) {
            setCantidadBCPsActivos(activos - 1);
        }
    }
    
    /**
     * Asocia una asignación de memoria a un proceso
     * 
     * @param bcp proceso al que se asigna
     * @param info información de la asignación
     * @param numeroBCP número del BCP en memoria
     */
    public void asociarAsignacionAProceso(BCP bcp, InfoAsignacion info, int numeroBCP) {
        estrategia.asociarAsignacionAProceso(bcp, info);
        actualizarBCP(numeroBCP, bcp);
    }
    
    private int calcularIndiceBCP(int numeroBCP) {
        return IDX_PRIMER_BCP + (numeroBCP * TAMANO_BCP);
    }
    
    // ========== GESTIÓN DE COLAS ==========
    
    /**
     * Agrega un proceso a la cola de trabajos
     */
    public void encolarTrabajo(int numeroBCP) {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS];
        if (tamano >= MAX_PROCESOS) {
            throw new IllegalStateException("Cola de trabajos llena");
        }
        memoria[IDX_COLA_TRABAJOS + tamano] = numeroBCP;
        memoria[IDX_TAMANO_COLA_TRABAJOS] = tamano + 1;
    }
    
    /**
     * Remueve y retorna el primer proceso de la cola de trabajos
     */
    public int desencolarTrabajo() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS];
        if (tamano == 0) return -1;
        
        int numeroBCP = (Integer) memoria[IDX_COLA_TRABAJOS];
        
        // Desplazar elementos
        for (int i = 0; i < tamano - 1; i++) {
            memoria[IDX_COLA_TRABAJOS + i] = memoria[IDX_COLA_TRABAJOS + i + 1];
        }
        
        memoria[IDX_COLA_TRABAJOS + tamano - 1] = null;
        memoria[IDX_TAMANO_COLA_TRABAJOS] = tamano - 1;
        
        return numeroBCP;
    }
    
    /**
     * Agrega un proceso a la cola de listos
     */
    public void encolarListo(int numeroBCP) {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_LISTOS];
        if (tamano >= MAX_PROCESOS) {
            throw new IllegalStateException("Cola de listos llena");
        }
        memoria[IDX_COLA_LISTOS + tamano] = numeroBCP;
        memoria[IDX_TAMANO_COLA_LISTOS] = tamano + 1;
    }
    
    /**
     * Remueve y retorna el primer proceso de la cola de listos
     */
    public int desencolarListo() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_LISTOS];
        if (tamano == 0) return -1;
        
        int numeroBCP = (Integer) memoria[IDX_COLA_LISTOS];
        
        // Desplazar elementos
        for (int i = 0; i < tamano - 1; i++) {
            memoria[IDX_COLA_LISTOS + i] = memoria[IDX_COLA_LISTOS + i + 1];
        }
        
        memoria[IDX_COLA_LISTOS + tamano - 1] = null;
        memoria[IDX_TAMANO_COLA_LISTOS] = tamano - 1;
        
        return numeroBCP;
    }
    
    public boolean colaListosVacia() {
        return (Integer) memoria[IDX_TAMANO_COLA_LISTOS] == 0;
    }
    
    public boolean colaTrabajosVacia() {
        return (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS] == 0;
    }
    
    public int[] obtenerColaListos() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_LISTOS];
        int[] cola = new int[tamano];
        for (int i = 0; i < tamano; i++) {
            Object val = memoria[IDX_COLA_LISTOS + i];
            cola[i] = val != null ? (Integer) val : -1;
        }
        return cola;
    }
    
    public int[] obtenerColaTrabajos() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS];
        int[] cola = new int[tamano];
        for (int i = 0; i < tamano; i++) {
            Object val = memoria[IDX_COLA_TRABAJOS + i];
            cola[i] = val != null ? (Integer) val : -1;
        }
        return cola;
    }
    
    // ========== METADATA Y CONTROL ==========
    
    public int getCantidadBCPsActivos() {
        return (Integer) memoria[IDX_BCPS_ACTIVOS];
    }
    
    private void setCantidadBCPsActivos(int cantidad) {
        memoria[IDX_BCPS_ACTIVOS] = cantidad;
    }
    
    public int getBCPEnEjecucion() {
        return (Integer) memoria[IDX_BCP_EN_EJECUCION];
    }
    
    public void setBCPEnEjecucion(int numeroBCP) {
        if (numeroBCP < -1 || numeroBCP >= MAX_PROCESOS) {
            throw new IllegalArgumentException("Número de BCP inválido: " + numeroBCP);
        }
        memoria[IDX_BCP_EN_EJECUCION] = numeroBCP;
    }
    
    public int generarNuevoIDProceso() {
        int id = (Integer) memoria[IDX_SIGUIENTE_ID];
        memoria[IDX_SIGUIENTE_ID] = id + 1;
        return id;
    }
    
    // ========== GESTIÓN DE ESTRATEGIAS ==========
    
    /**
     * Cambia la estrategia de particionamiento
     * ADVERTENCIA: Esto reiniciará toda la memoria de usuario
     * 
     * @param nuevaEstrategia nueva estrategia a utilizar
     */
    public void cambiarEstrategia(IEstrategiaParticionamiento nuevaEstrategia) {
        System.out.println("[MEMORIA PRINCIPAL] Cambiando estrategia de: " + 
                         estrategia.getNombre() + " a: " + nuevaEstrategia.getNombre());
        
        // Limpiar área de usuario
        for (int i = inicioUsuario; i < tamanoTotal; i++) {
            memoria[i] = null;
        }
        
        // Cambiar estrategia
        this.estrategia = nuevaEstrategia;
        memoria[IDX_TIPO_ESTRATEGIA] = estrategia.getNombre();
        
        // Inicializar nueva estrategia
        inicializarEstrategia();
    }
    
    public IEstrategiaParticionamiento getEstrategia() {
        return estrategia;
    }
    
    public String getNombreEstrategia() {
        return estrategia.getNombre();
    }
    
    // ========== INFORMACIÓN Y REPORTES ==========
    
    public Object[] getMemoriaCompleta() {
        return memoria;
    }
    
    public int getTamanoTotal() {
        return tamanoTotal;
    }
    
    public int getTamanoSO() {
        return tamanoSO;
    }
    
    public int getTamanoUsuario() {
        return tamanoUsuario;
    }
    
    public int getInicioUsuario() {
        return inicioUsuario;
    }
    
    public int getMaxProcesos() {
        return MAX_PROCESOS;
    }
    
    /**
     * Genera un reporte completo del estado de la memoria
     */
    public String generarReporte() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== MEMORIA PRINCIPAL ==========\n");
        sb.append(String.format("Tamaño total: %d KB\n", tamanoTotal));
        sb.append(String.format("Área SO: %d KB\n", tamanoSO));
        sb.append(String.format("Área Usuario: %d KB\n", tamanoUsuario));
        sb.append(String.format("Estrategia activa: %s\n", estrategia.getNombre()));
        sb.append(String.format("BCPs activos: %d/%d\n", getCantidadBCPsActivos(), MAX_PROCESOS));
        sb.append(String.format("Proceso en ejecución: %d\n", getBCPEnEjecucion()));
        sb.append(String.format("Cola de trabajos: %d procesos\n", 
                               (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS]));
        sb.append(String.format("Cola de listos: %d procesos\n", 
                               (Integer) memoria[IDX_TAMANO_COLA_LISTOS]));
        sb.append("\n");
        sb.append(estrategia.generarReporte());
        sb.append("======================================\n");
        
        return sb.toString();
    }
    
    /**
     * Reinicia toda la memoria a su estado inicial
     */
    public void reiniciar() {
        System.out.println("[MEMORIA PRINCIPAL] Reiniciando...");
        
        // Reiniciar estrategia
        estrategia.reiniciar();
        
        // Limpiar BCPs
        for (int i = 0; i < MAX_PROCESOS; i++) {
            int indice = calcularIndiceBCP(i);
            for (int j = 0; j < TAMANO_BCP; j++) {
                memoria[indice + j] = null;
            }
        }
        
        // Reiniciar metadata
        memoria[IDX_BCPS_ACTIVOS] = 0;
        memoria[IDX_BCP_EN_EJECUCION] = -1;
        memoria[IDX_SIGUIENTE_ID] = 1;
        memoria[IDX_TAMANO_COLA_TRABAJOS] = 0;
        memoria[IDX_TAMANO_COLA_LISTOS] = 0;
        
        System.out.println("[MEMORIA PRINCIPAL] Reinicio completo");
    }
}