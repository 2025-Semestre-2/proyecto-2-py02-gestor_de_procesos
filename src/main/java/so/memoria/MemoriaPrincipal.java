package so.memoria;

import so.instrucciones.Instruccion;
import so.gestordeprocesos.BCP;

/**
 * Gestiona la memoria principal del sistema operativo.
 * 
 * Estructura:
 * - [0-199]: Área del Sistema Operativo
 *   - [0-9]: Metadata
 *   - [10-134]: 5 BCPs (25 entradas cada uno)
 *   - [135-199]: Colas y estructuras de control
 * - [200-511]: Área de Usuario (instrucciones de procesos)
 * 
 * @author dylan
 */
public class MemoriaPrincipal {
    
    // ========== CONSTANTES DE CONFIGURACIÓN ==========
    private final int TAMANO_TOTAL;
    private final int TAMANO_SO;
    private final int INICIO_USUARIO;
    private final int TAMANO_USUARIO;
    private final int TAMANO_BCP = 25;
    private final int MAX_PROCESOS = 5;
    
    // ========== ÍNDICES EN MEMORIA ==========
    // Metadata
    private final int IDX_TAMANO_TOTAL = 0;
    private final int IDX_TAMANO_SO = 1;
    private final int IDX_TAMANO_USUARIO = 2;
    private final int IDX_BCPS_ACTIVOS = 3;
    private final int IDX_BCP_EN_EJECUCION = 4;
    private final int IDX_SIGUIENTE_ID = 5;
    
    // BCPs
    private final int IDX_PRIMER_BCP = 10;
    
    // Colas
    private final int IDX_COLA_TRABAJOS = 135;
    private final int IDX_TAMANO_COLA_TRABAJOS = 140;
    private final int IDX_COLA_LISTOS = 141;
    private final int IDX_TAMANO_COLA_LISTOS = 146;
    
    // ========== MEMORIA ==========
    private final Object[] memoria;
    
    /**
     * Constructor con tamaño por defecto (512 KB)
     */
    public MemoriaPrincipal() {
        this(512);
    }
    
    /**
     * Constructor con tamaño personalizado
     * @param tamanoTotal tamaño total de la memoria en KB
     */
    public MemoriaPrincipal(int tamanoTotal) {
        if (tamanoTotal < 300) {
            throw new IllegalArgumentException("El tamaño de memoria debe ser al menos 300 KB");
        }
        
        this.TAMANO_TOTAL = tamanoTotal;
        this.TAMANO_SO = 200;
        this.INICIO_USUARIO = 200;
        this.TAMANO_USUARIO = tamanoTotal - TAMANO_SO;
        this.memoria = new Object[TAMANO_TOTAL];
        
        inicializarMemoria();
    }
    
    /**
     * Inicializa la memoria con valores por defecto
     */
    private void inicializarMemoria() {
        // Limpiar toda la memoria
        for (int i = 0; i < TAMANO_TOTAL; i++) {
            memoria[i] = null;
        }
        
        // Inicializar metadata
        memoria[IDX_TAMANO_TOTAL] = TAMANO_TOTAL;
        memoria[IDX_TAMANO_SO] = TAMANO_SO;
        memoria[IDX_TAMANO_USUARIO] = TAMANO_USUARIO;
        memoria[IDX_BCPS_ACTIVOS] = 0;
        memoria[IDX_BCP_EN_EJECUCION] = -1;
        memoria[IDX_SIGUIENTE_ID] = 1; // IDs comienzan en 1
        
        // Inicializar colas
        memoria[IDX_TAMANO_COLA_TRABAJOS] = 0;
        memoria[IDX_TAMANO_COLA_LISTOS] = 0;
    }
    
    // ========== GESTIÓN DE METADATA ==========
    
    /**
     * Obtiene la cantidad de BCPs activos en memoria
     */
    public int getCantidadBCPsActivos() {
        return (Integer) memoria[IDX_BCPS_ACTIVOS];
    }
    
    /**
     * Establece la cantidad de BCPs activos
     */
    private void setCantidadBCPsActivos(int cantidad) {
        memoria[IDX_BCPS_ACTIVOS] = cantidad;
    }
    
    /**
     * Obtiene el índice del BCP en ejecución (-1 si ninguno)
     */
    public int getBCPEnEjecucion() {
        return (Integer) memoria[IDX_BCP_EN_EJECUCION];
    }
    
    /**
     * Establece el BCP en ejecución
     */
    public void setBCPEnEjecucion(int numeroBCP) {
        if (numeroBCP < -1 || numeroBCP >= MAX_PROCESOS) {
            throw new IllegalArgumentException("Número de BCP inválido: " + numeroBCP);
        }
        memoria[IDX_BCP_EN_EJECUCION] = numeroBCP;
    }
    
    /**
     * Genera y retorna un nuevo ID único para un proceso
     */
    public int generarNuevoIDProceso() {
        int id = (Integer) memoria[IDX_SIGUIENTE_ID];
        memoria[IDX_SIGUIENTE_ID] = id + 1;
        return id;
    }
    
    // ========== GESTIÓN DE BCPs ==========
    
    /**
     * Busca un espacio libre para un BCP
     * @return número de BCP (0-4) o -1 si no hay espacio
     */
    public int buscarBCPLibre() {
        for (int i = 0; i < MAX_PROCESOS; i++) {
            int indice = calcularIndiceBCP(i);
            // Si el primer campo (ID) es null, el BCP está libre
            if (memoria[indice] == null) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Crea un BCP en memoria
     * @param bcp BCP a crear
     * @return número de BCP asignado (0-4) o -1 si no hay espacio
     */
    public int crearBCP(BCP bcp) {
        int numeroBCP = buscarBCPLibre();
        if (numeroBCP < 0) {
            return -1; // no hay espacio
        }
        
        int indice = calcularIndiceBCP(numeroBCP);
        bcp.guardarEnMemoria(memoria, indice);
        
        int activos = getCantidadBCPsActivos();
        setCantidadBCPsActivos(activos + 1);
        
        return numeroBCP;
    }
    
    /**
     * Obtiene un BCP desde memoria
     * @param numeroBCP número de BCP (0-4)
     * @return BCP o null si no existe
     */
    public BCP obtenerBCP(int numeroBCP) {
        if (numeroBCP < 0 || numeroBCP >= MAX_PROCESOS) {
            return null;
        }
        
        int indice = calcularIndiceBCP(numeroBCP);
        
        // Verificar si el BCP existe (ID no es null)
        if (memoria[indice] == null) {
            return null;
        }
        
        return BCP.cargarDesdeMemoria(memoria, indice);
    }
    
    /**
     * Actualiza un BCP en memoria
     * @param numeroBCP número de BCP (0-4)
     * @param bcp BCP actualizado
     */
    public void actualizarBCP(int numeroBCP, BCP bcp) {
        if (numeroBCP < 0 || numeroBCP >= MAX_PROCESOS) {
            throw new IllegalArgumentException("Número de BCP inválido: " + numeroBCP);
        }
        
        int indice = calcularIndiceBCP(numeroBCP);
        bcp.guardarEnMemoria(memoria, indice);
    }
    
    /**
     * Libera un BCP de memoria
     * @param numeroBCP número de BCP (0-4)
     */
    public void liberarBCP(int numeroBCP) {
        if (numeroBCP < 0 || numeroBCP >= MAX_PROCESOS) {
            return;
        }
        
        int indice = calcularIndiceBCP(numeroBCP);
        
        // Limpiar las 25 posiciones del BCP
        for (int i = 0; i < TAMANO_BCP; i++) {
            memoria[indice + i] = null;
        }
        
        int activos = getCantidadBCPsActivos();
        if (activos > 0) {
            setCantidadBCPsActivos(activos - 1);
        }
    }
    
    /**
     * Calcula el índice en memoria donde comienza un BCP
     * @param numeroBCP número de BCP (0-4)
     * @return índice en el array de memoria
     */
    private int calcularIndiceBCP(int numeroBCP) {
        return IDX_PRIMER_BCP + (numeroBCP * TAMANO_BCP);
    }
    
    // ========== GESTIÓN DE COLAS ==========
    
    /**
     * Encola un BCP en la cola de trabajos
     * @param numeroBCP número de BCP (0-4)
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
     * Desencola un BCP de la cola de trabajos (FIFO)
     * @return número de BCP o -1 si la cola está vacía
     */
    public int desencolarTrabajo() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS];
        if (tamano == 0) {
            return -1;
        }
        
        // Obtener el primer elemento
        int numeroBCP = (Integer) memoria[IDX_COLA_TRABAJOS];
        
        // Desplazar todos los elementos una posición hacia adelante
        for (int i = 0; i < tamano - 1; i++) {
            memoria[IDX_COLA_TRABAJOS + i] = memoria[IDX_COLA_TRABAJOS + i + 1];
        }
        
        // Limpiar la última posición
        memoria[IDX_COLA_TRABAJOS + tamano - 1] = null;
        memoria[IDX_TAMANO_COLA_TRABAJOS] = tamano - 1;
        
        return numeroBCP;
    }
    
    /**
     * Encola un BCP en la cola de listos
     * @param numeroBCP número de BCP (0-4)
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
     * Desencola un BCP de la cola de listos (FIFO)
     * @return número de BCP o -1 si la cola está vacía
     */
    public int desencolarListo() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_LISTOS];
        if (tamano == 0) {
            return -1;
        }
        
        // Obtener el primer elemento
        int numeroBCP = (Integer) memoria[IDX_COLA_LISTOS];
        
        // Desplazar todos los elementos una posición hacia adelante
        for (int i = 0; i < tamano - 1; i++) {
            memoria[IDX_COLA_LISTOS + i] = memoria[IDX_COLA_LISTOS + i + 1];
        }
        
        // Limpiar la última posición
        memoria[IDX_COLA_LISTOS + tamano - 1] = null;
        memoria[IDX_TAMANO_COLA_LISTOS] = tamano - 1;
        
        return numeroBCP;
    }
    
    /**
     * Verifica si la cola de listos está vacía
     */
    public boolean colaListosVacia() {
        return (Integer) memoria[IDX_TAMANO_COLA_LISTOS] == 0;
    }
    
    /**
     * Verifica si la cola de trabajos está vacía
     */
    public boolean colaTrabajosVacia() {
        return (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS] == 0;
    }
    
    /**
     * Obtiene una copia de la cola de listos para visualización
     * @return array con los números de BCP en la cola
     */
    public int[] obtenerColaListos() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_LISTOS];
        int[] cola = new int[tamano];
        
        for (int i = 0; i < tamano; i++) {
            cola[i] = (Integer) memoria[IDX_COLA_LISTOS + i];
        }
        
        return cola;
    }
    
    /**
     * Obtiene una copia de la cola de trabajos para visualización
     * @return array con los números de BCP en la cola
     */
    public int[] obtenerColaTrabajos() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS];
        int[] cola = new int[tamano];
        
        for (int i = 0; i < tamano; i++) {
            cola[i] = (Integer) memoria[IDX_COLA_TRABAJOS + i];
        }
        
        return cola;
    }
    
    // ========== GESTIÓN DE INSTRUCCIONES (ÁREA USUARIO) ==========
    
    /**
     * Busca espacio libre contiguo en el área de usuario
     * @param tamanoRequerido número de posiciones necesarias
     * @return dirección de inicio o -1 si no hay espacio
     */
    private int buscarEspacioLibreUsuario(int tamanoRequerido) {
        if (tamanoRequerido > TAMANO_USUARIO) {
            return -1;
        }
        
        int consecutivosLibres = 0;
        int inicio = -1;
        
        for (int i = INICIO_USUARIO; i < TAMANO_TOTAL; i++) {
            if (memoria[i] == null) {
                if (consecutivosLibres == 0) {
                    inicio = i;
                }
                consecutivosLibres++;
                
                if (consecutivosLibres >= tamanoRequerido) {
                    return inicio;
                }
            } else {
                consecutivosLibres = 0;
                inicio = -1;
            }
        }
        
        return -1;
    }
    
    /**
     * Carga instrucciones en el área de usuario
     * @param instrucciones lista de instrucciones a cargar
     * @return dirección base donde se cargaron o -1 si no hay espacio
     */
    public int cargarInstrucciones(Instruccion[] instrucciones) {
        int direccionBase = buscarEspacioLibreUsuario(instrucciones.length);
        
        if (direccionBase < 0) {
            return -1; // no hay espacio suficiente
        }
        
        // Cargar las instrucciones
        for (int i = 0; i < instrucciones.length; i++) {
            memoria[direccionBase + i] = instrucciones[i];
        }
        
        return direccionBase;
    }
    
    /**
     * Obtiene una instrucción desde el área de usuario
     * @param direccion dirección de memoria
     * @return instrucción o null si no existe
     */
    public Instruccion obtenerInstruccion(int direccion) {
        if (direccion < INICIO_USUARIO || direccion >= TAMANO_TOTAL) {
            return null;
        }
        
        Object obj = memoria[direccion];
        if (obj instanceof Instruccion) {
            return (Instruccion) obj;
        }
        
        return null;
    }
    
    /**
     * Libera un rango de memoria en el área de usuario
     * @param base dirección de inicio
     * @param tamano número de posiciones a liberar
     */
    public void liberarInstrucciones(int base, int tamano) {
        if (base < INICIO_USUARIO || base >= TAMANO_TOTAL) {
            return;
        }
        
        for (int i = 0; i < tamano; i++) {
            int direccion = base + i;
            if (direccion < TAMANO_TOTAL) {
                memoria[direccion] = null;
            }
        }
    }
    
    /**
     * Calcula el espacio libre en el área de usuario
     * @return número de posiciones libres
     */
    public int getEspacioLibreUsuario() {
        int libres = 0;
        
        for (int i = INICIO_USUARIO; i < TAMANO_TOTAL; i++) {
            if (memoria[i] == null) {
                libres++;
            }
        }
        
        return libres;
    }
    
    // ========== INFORMACIÓN Y UTILIDADES ==========
    
    /**
     * Obtiene el array completo de memoria (para debugging)
     */
    public Object[] getMemoriaCompleta() {
        return memoria;
    }
    
    /**
     * Obtiene el tamaño total de la memoria
     */
    public int getTamanoTotal() {
        return TAMANO_TOTAL;
    }
    
    /**
     * Obtiene el tamaño del área del sistema operativo
     */
    public int getTamanoSO() {
        return TAMANO_SO;
    }
    
    /**
     * Obtiene el tamaño del área de usuario
     */
    public int getTamanoUsuario() {
        return TAMANO_USUARIO;
    }
    
    /**
     * Obtiene la dirección de inicio del área de usuario
     */
    public int getInicioUsuario() {
        return INICIO_USUARIO;
    }
    
    /**
     * Obtiene el número máximo de procesos simultáneos
     */
    public int getMaxProcesos() {
        return MAX_PROCESOS;
    }
    
    /**
     * Genera un reporte del estado de la memoria
     */
    public String generarReporte() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== ESTADO DE LA MEMORIA PRINCIPAL ==========\n");
        sb.append(String.format("Tamaño total: %d KB\n", TAMANO_TOTAL));
        sb.append(String.format("Área SO: %d KB\n", TAMANO_SO));
        sb.append(String.format("Área Usuario: %d KB\n", TAMANO_USUARIO));
        sb.append(String.format("BCPs activos: %d/%d\n", getCantidadBCPsActivos(), MAX_PROCESOS));
        sb.append(String.format("BCP en ejecución: %d\n", getBCPEnEjecucion()));
        sb.append(String.format("Espacio libre usuario: %d KB\n", getEspacioLibreUsuario()));
        sb.append(String.format("Cola de trabajos: %d procesos\n", 
                (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS]));
        sb.append(String.format("Cola de listos: %d procesos\n", 
                (Integer) memoria[IDX_TAMANO_COLA_LISTOS]));
        sb.append("===================================================\n");
        
        return sb.toString();
    }
}