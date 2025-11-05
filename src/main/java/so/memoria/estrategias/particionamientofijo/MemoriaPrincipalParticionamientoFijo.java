package so.memoria.estrategias.particionamientofijo;

import so.instrucciones.Instruccion;
import so.gestordeprocesos.BCP;

/**
 * Gestión de Memoria Principal con Particionamiento Fijo.
 * 
 * Estructura de Memoria:
 * - [0-999]: Área del Sistema Operativo
 *   - [0-9]: Metadata general
 *   - [10-634]: 25 BCPs (25 entradas cada uno)
 *   - [635-749]: Tabla de Particiones (metadata de particiones)
 *   - [750-874]: Cola de Trabajos (hasta 25 procesos)
 *   - [875-999]: Cola de Listos (hasta 25 procesos)
 * - [1000-9999]: Área de Usuario (9000 KB para instrucciones con particionamiento fijo)
 * 
 * Tipos de Particionamiento:
 * - IGUAL: Todas las particiones del mismo tamaño
 * - DESIGUAL: Particiones con tamaños incrementales (2, 4, 6, 8, ...)
 * 
 * @author dylan
 */
public class MemoriaPrincipalParticionamientoFijo {
    
    // ========== CONSTANTES DE CONFIGURACIÓN ==========
    private final int TAMANO_TOTAL = 10000; // 10,000 KB
    private final int TAMANO_SO = 1000;     // 1,000 KB para SO
    private final int INICIO_USUARIO = 1000;
    private final int TAMANO_USUARIO = 9000; // 9,000 KB para procesos
    private final int TAMANO_BCP = 28;      // ACTUALIZADO: 28 atributos (25 originales + 3 de particionamiento)
    private final int MAX_PROCESOS = 25;
    
    // ========== ÍNDICES EN MEMORIA - METADATA ==========
    private final int IDX_TAMANO_TOTAL = 0;
    private final int IDX_TAMANO_SO = 1;
    private final int IDX_TAMANO_USUARIO = 2;
    private final int IDX_BCPS_ACTIVOS = 3;
    private final int IDX_BCP_EN_EJECUCION = 4;
    private final int IDX_SIGUIENTE_ID = 5;
    private final int IDX_TIPO_PARTICIONAMIENTO = 6; // 0=IGUAL, 1=DESIGUAL
    private final int IDX_NUM_PARTICIONES = 7;
    private final int IDX_TAMANO_PARTICION_IGUAL = 8; // Solo para tipo IGUAL
    
    // ========== ÍNDICES - BCPs ==========
    private final int IDX_PRIMER_BCP = 10;
    
    // ========== ÍNDICES - TABLA DE PARTICIONES ==========
    // Cada partición: [inicio, tamaño, estado(0=libre,1=ocupada), idProceso, numeroBCP]
    // 25 BCPs * 28 atributos = 700 posiciones → BCPs ocupan [10-709]
    private final int IDX_TABLA_PARTICIONES = 710;
    private final int TAMANO_ENTRADA_PARTICION = 5;
    private final int MAX_PARTICIONES = 58; // (999-710)/5 = 57.8 ≈ 58 particiones máx
    
    // ========== ÍNDICES - COLAS ==========
    private final int IDX_COLA_TRABAJOS = 750;
    private final int IDX_TAMANO_COLA_TRABAJOS = 775;
    private final int IDX_COLA_LISTOS = 800;
    private final int IDX_TAMANO_COLA_LISTOS = 825;
    
    // ========== MEMORIA ==========
    private final Object[] memoria;
    
    // ========== ENUMERACIÓN ==========
    public enum TipoParticionamiento {
        IGUAL,      // Todas las particiones del mismo tamaño
        DESIGUAL    // Particiones con tamaños incrementales (2, 4, 6, 8, ...)
    }
    
    /**
     * Constructor para Particionamiento Fijo con Tamaños IGUALES
     * 
     * @param tamanoParticion tamaño de cada partición en KB
     */
    public MemoriaPrincipalParticionamientoFijo(int tamanoParticion) {
        this.memoria = new Object[TAMANO_TOTAL];
        inicializarMemoria();
        configurarParticionamientoIgual(tamanoParticion);
    }
    
    /**
     * Constructor para Particionamiento Fijo con Tamaños DESIGUALES
     * Tamaños: 2, 4, 6, 8, 10, ... hasta llenar el área de usuario
     * 
     * @param tipo debe ser TipoParticionamiento.DESIGUAL
     */
    public MemoriaPrincipalParticionamientoFijo(TipoParticionamiento tipo) {
        if (tipo != TipoParticionamiento.DESIGUAL) {
            throw new IllegalArgumentException("Use el otro constructor para particionamiento igual");
        }
        this.memoria = new Object[TAMANO_TOTAL];
        inicializarMemoria();
        configurarParticionamientoDesigual();
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
        memoria[IDX_SIGUIENTE_ID] = 1;
        memoria[IDX_NUM_PARTICIONES] = 0;
        
        // Inicializar colas
        memoria[IDX_TAMANO_COLA_TRABAJOS] = 0;
        memoria[IDX_TAMANO_COLA_LISTOS] = 0;
        
        // Inicializar tabla de particiones
        for (int i = 0; i < MAX_PARTICIONES; i++) {
            int base = IDX_TABLA_PARTICIONES + (i * TAMANO_ENTRADA_PARTICION);
            memoria[base + 0] = -1;  // inicio
            memoria[base + 1] = 0;   // tamaño
            memoria[base + 2] = 0;   // estado (libre)
            memoria[base + 3] = -1;  // idProceso
            memoria[base + 4] = -1;  // numeroBCP
        }
    }
    
    /**
     * Configura particionamiento con tamaños iguales
     */
    private void configurarParticionamientoIgual(int tamanoParticion) {
        if (tamanoParticion <= 0 || tamanoParticion > TAMANO_USUARIO) {
            throw new IllegalArgumentException("Tamaño de partición inválido: " + tamanoParticion);
        }
        
        memoria[IDX_TIPO_PARTICIONAMIENTO] = 0; // IGUAL
        memoria[IDX_TAMANO_PARTICION_IGUAL] = tamanoParticion;
        
        int numParticiones = TAMANO_USUARIO / tamanoParticion;
        
        if (numParticiones > MAX_PARTICIONES) {
            numParticiones = MAX_PARTICIONES;
        }
        
        memoria[IDX_NUM_PARTICIONES] = numParticiones;
        
        // Crear tabla de particiones
        int direccionActual = INICIO_USUARIO;
        
        for (int i = 0; i < numParticiones; i++) {
            int base = IDX_TABLA_PARTICIONES + (i * TAMANO_ENTRADA_PARTICION);
            memoria[base + 0] = direccionActual;  // inicio
            memoria[base + 1] = tamanoParticion;  // tamaño
            memoria[base + 2] = 0;                // estado (libre)
            memoria[base + 3] = -1;               // idProceso
            memoria[base + 4] = -1;               // numeroBCP
            
            direccionActual += tamanoParticion;
        }
        
        System.out.println("[MEMORIA] Particionamiento Fijo IGUAL configurado:");
        System.out.println("  - Tamaño de partición: " + tamanoParticion + " KB");
        System.out.println("  - Número de particiones: " + numParticiones);
        System.out.println("  - Espacio total usado: " + (numParticiones * tamanoParticion) + " KB");
    }
    
    /**
     * Configura particionamiento con tamaños desiguales (2, 4, 6, 8, ...)
     */
    private void configurarParticionamientoDesigual() {
        memoria[IDX_TIPO_PARTICIONAMIENTO] = 1; // DESIGUAL
        memoria[IDX_TAMANO_PARTICION_IGUAL] = -1; // No aplica
        
        int direccionActual = INICIO_USUARIO;
        int numParticiones = 0;
        int tamanoActual = 2; // Comienza en 2 KB
        int espacioRestante = TAMANO_USUARIO;
        
        while (espacioRestante >= tamanoActual && numParticiones < MAX_PARTICIONES) {
            int base = IDX_TABLA_PARTICIONES + (numParticiones * TAMANO_ENTRADA_PARTICION);
            memoria[base + 0] = direccionActual;  // inicio
            memoria[base + 1] = tamanoActual;     // tamaño
            memoria[base + 2] = 0;                // estado (libre)
            memoria[base + 3] = -1;               // idProceso
            memoria[base + 4] = -1;               // numeroBCP
            
            direccionActual += tamanoActual;
            espacioRestante -= tamanoActual;
            numParticiones++;
            tamanoActual += 2; // Incrementar de 2 en 2
        }
        
        memoria[IDX_NUM_PARTICIONES] = numParticiones;
        
        System.out.println("[MEMORIA] Particionamiento Fijo DESIGUAL configurado:");
        System.out.println("  - Número de particiones: " + numParticiones);
        System.out.println("  - Tamaños: 2, 4, 6, 8, ..., " + (2 + (numParticiones-1)*2) + " KB");
        System.out.println("  - Espacio total usado: " + (TAMANO_USUARIO - espacioRestante) + " KB");
        System.out.println("  - Espacio no utilizado: " + espacioRestante + " KB");
    }
    
    // ========== GESTIÓN DE PARTICIONES ==========
    
    /**
     * Busca una partición libre que pueda contener el proceso
     * 
     * @param tamanoRequerido tamaño del proceso en KB (número de instrucciones)
     * @return índice de partición (0-22) o -1 si no hay espacio
     */
    private int buscarParticionLibre(int tamanoRequerido) {
        int numParticiones = (Integer) memoria[IDX_NUM_PARTICIONES];
        int mejorParticion = -1;
        int menorDesperdicio = Integer.MAX_VALUE;
        
        // Buscar la partición más pequeña que quepa (best-fit para minimizar fragmentación interna)
        for (int i = 0; i < numParticiones; i++) {
            int base = IDX_TABLA_PARTICIONES + (i * TAMANO_ENTRADA_PARTICION);
            int tamanoParticion = (Integer) memoria[base + 1];
            int estado = (Integer) memoria[base + 2];
            
            if (estado == 0 && tamanoParticion >= tamanoRequerido) {
                int desperdicio = tamanoParticion - tamanoRequerido;
                if (desperdicio < menorDesperdicio) {
                    menorDesperdicio = desperdicio;
                    mejorParticion = i;
                }
            }
        }
        
        return mejorParticion;
    }
    
    /**
     * Marca una partición como ocupada
     */
    private void ocuparParticion(int indiceParticion, int idProceso, int numeroBCP) {
        int base = IDX_TABLA_PARTICIONES + (indiceParticion * TAMANO_ENTRADA_PARTICION);
        memoria[base + 2] = 1;              // estado = ocupada
        memoria[base + 3] = idProceso;      // idProceso
        memoria[base + 4] = numeroBCP;      // numeroBCP
    }
    
    /**
     * Libera una partición
     */
    private void liberarParticion(int indiceParticion) {
        int base = IDX_TABLA_PARTICIONES + (indiceParticion * TAMANO_ENTRADA_PARTICION);
        memoria[base + 2] = 0;   // estado = libre
        memoria[base + 3] = -1;  // idProceso
        memoria[base + 4] = -1;  // numeroBCP
        
        // Limpiar las instrucciones de esa partición
        int inicio = (Integer) memoria[base + 0];
        int tamano = (Integer) memoria[base + 1];
        
        for (int i = inicio; i < inicio + tamano && i < TAMANO_TOTAL; i++) {
            memoria[i] = null;
        }
    }
    
    /**
     * Obtiene información de una partición
     */
    public String getInfoParticion(int indiceParticion) {
        if (indiceParticion < 0 || indiceParticion >= (Integer) memoria[IDX_NUM_PARTICIONES]) {
            return "Partición inválida";
        }
        
        int base = IDX_TABLA_PARTICIONES + (indiceParticion * TAMANO_ENTRADA_PARTICION);
        int inicio = (Integer) memoria[base + 0];
        int tamano = (Integer) memoria[base + 1];
        int estado = (Integer) memoria[base + 2];
        int idProceso = (Integer) memoria[base + 3];
        
        return String.format("Partición %d: [%d-%d] %d KB - %s%s",
            indiceParticion,
            inicio,
            inicio + tamano - 1,
            tamano,
            estado == 0 ? "LIBRE" : "OCUPADA",
            estado == 1 ? " (Proceso ID: " + idProceso + ")" : ""
        );
    }
    
    // ========== GESTIÓN DE BCPs ==========
    
    public int buscarBCPLibre() {
        for (int i = 0; i < MAX_PROCESOS; i++) {
            int indice = calcularIndiceBCP(i);
            if (memoria[indice] == null) {
                return i;
            }
        }
        return -1;
    }
    
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
    
    public void actualizarBCP(int numeroBCP, BCP bcp) {
        if (numeroBCP < 0 || numeroBCP >= MAX_PROCESOS) {
            throw new IllegalArgumentException("Número de BCP inválido: " + numeroBCP);
        }
        
        int indice = calcularIndiceBCP(numeroBCP);
        bcp.guardarEnMemoria(memoria, indice);
    }
    
    public void liberarBCP(int numeroBCP) {
        if (numeroBCP < 0 || numeroBCP >= MAX_PROCESOS) {
            return;
        }
        
        BCP bcp = obtenerBCP(numeroBCP);
        if (bcp != null) {
            // Liberar la partición asociada
            int indiceParticion = bcp.getIndiceParticion();
            if (indiceParticion >= 0) {
                liberarParticion(indiceParticion);
            }
        }
        
        int indice = calcularIndiceBCP(numeroBCP);
        for (int i = 0; i < TAMANO_BCP; i++) {
            memoria[indice + i] = null;
        }
        
        int activos = getCantidadBCPsActivos();
        if (activos > 0) {
            setCantidadBCPsActivos(activos - 1);
        }
    }
    
    private int calcularIndiceBCP(int numeroBCP) {
        return IDX_PRIMER_BCP + (numeroBCP * TAMANO_BCP);
    }
    
    // ========== GESTIÓN DE INSTRUCCIONES CON PARTICIONAMIENTO ==========
    
    /**
     * Carga instrucciones en una partición disponible
     * 
     * @param instrucciones array de instrucciones
     * @return array [direccionBase, indiceParticion] o null si no hay espacio
     */
    public int[] cargarInstruccionesEnParticion(Instruccion[] instrucciones) {
        int tamanoRequerido = instrucciones.length;
        int indiceParticion = buscarParticionLibre(tamanoRequerido);
        
        if (indiceParticion < 0) {
            System.out.println("[MEMORIA] No hay partición disponible para " + tamanoRequerido + " instrucciones");
            return null;
        }
        
        // Obtener información de la partición
        int base = IDX_TABLA_PARTICIONES + (indiceParticion * TAMANO_ENTRADA_PARTICION);
        int direccionBase = (Integer) memoria[base + 0];
        int tamanoParticion = (Integer) memoria[base + 1];
        
        // Cargar instrucciones
        for (int i = 0; i < instrucciones.length; i++) {
            memoria[direccionBase + i] = instrucciones[i];
        }
        
        int fragmentacionInterna = tamanoParticion - tamanoRequerido;
        
        System.out.println("[MEMORIA] Instrucciones cargadas en partición " + indiceParticion);
        System.out.println("  - Dirección base: " + direccionBase);
        System.out.println("  - Tamaño partición: " + tamanoParticion + " KB");
        System.out.println("  - Tamaño proceso: " + tamanoRequerido + " KB");
        System.out.println("  - Fragmentación interna: " + fragmentacionInterna + " KB");
        
        return new int[]{direccionBase, indiceParticion};
    }
    
    /**
     * Asocia una partición a un proceso
     */
    public void asignarParticionAProceso(int indiceParticion, int idProceso, int numeroBCP) {
        ocuparParticion(indiceParticion, idProceso, numeroBCP);
    }
    
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
    
    // ========== GESTIÓN DE COLAS ==========
    
    public void encolarTrabajo(int numeroBCP) {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS];
        if (tamano >= MAX_PROCESOS) {
            throw new IllegalStateException("Cola de trabajos llena");
        }
        memoria[IDX_COLA_TRABAJOS + tamano] = numeroBCP;
        memoria[IDX_TAMANO_COLA_TRABAJOS] = tamano + 1;
    }
    
    public int desencolarTrabajo() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS];
        if (tamano == 0) return -1;
        
        int numeroBCP = (Integer) memoria[IDX_COLA_TRABAJOS];
        
        for (int i = 0; i < tamano - 1; i++) {
            memoria[IDX_COLA_TRABAJOS + i] = memoria[IDX_COLA_TRABAJOS + i + 1];
        }
        
        memoria[IDX_COLA_TRABAJOS + tamano - 1] = null;
        memoria[IDX_TAMANO_COLA_TRABAJOS] = tamano - 1;
        
        return numeroBCP;
    }
    
    public void encolarListo(int numeroBCP) {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_LISTOS];
        if (tamano >= MAX_PROCESOS) {
            throw new IllegalStateException("Cola de listos llena");
        }
        memoria[IDX_COLA_LISTOS + tamano] = numeroBCP;
        memoria[IDX_TAMANO_COLA_LISTOS] = tamano + 1;
    }
    
    public int desencolarListo() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_LISTOS];
        if (tamano == 0) return -1;
        
        int numeroBCP = (Integer) memoria[IDX_COLA_LISTOS];
        
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
            cola[i] = (Integer) memoria[IDX_COLA_LISTOS + i];
        }
        return cola;
    }
    
    public int[] obtenerColaTrabajos() {
        int tamano = (Integer) memoria[IDX_TAMANO_COLA_TRABAJOS];
        int[] cola = new int[tamano];
        for (int i = 0; i < tamano; i++) {
            cola[i] = (Integer) memoria[IDX_COLA_TRABAJOS + i];
        }
        return cola;
    }
    
    // ========== METADATA ==========
    
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
    
    // ========== INFORMACIÓN Y ESTADÍSTICAS ==========
    
    public Object[] getMemoriaCompleta() {
        return memoria;
    }
    
    public int getTamanoTotal() {
        return TAMANO_TOTAL;
    }
    
    public int getTamanoSO() {
        return TAMANO_SO;
    }
    
    public int getTamanoUsuario() {
        return TAMANO_USUARIO;
    }
    
    public int getInicioUsuario() {
        return INICIO_USUARIO;
    }
    
    public int getMaxProcesos() {
        return MAX_PROCESOS;
    }
    
    public int getNumeroParticiones() {
        return (Integer) memoria[IDX_NUM_PARTICIONES];
    }
    
    public TipoParticionamiento getTipoParticionamiento() {
        int tipo = (Integer) memoria[IDX_TIPO_PARTICIONAMIENTO];
        return tipo == 0 ? TipoParticionamiento.IGUAL : TipoParticionamiento.DESIGUAL;
    }
    
    /**
     * Calcula el espacio libre total (suma de particiones libres)
     */
    public int getEspacioLibreTotal() {
        int libre = 0;
        int numParticiones = (Integer) memoria[IDX_NUM_PARTICIONES];
        
        for (int i = 0; i < numParticiones; i++) {
            int base = IDX_TABLA_PARTICIONES + (i * TAMANO_ENTRADA_PARTICION);
            int estado = (Integer) memoria[base + 2];
            int tamano = (Integer) memoria[base + 1];
            
            if (estado == 0) {
                libre += tamano;
            }
        }
        
        return libre;
    }
    
    /**
     * Calcula la fragmentación interna total
     */
    public int getFragmentacionInternaTotal() {
        int fragmentacion = 0;
        int numParticiones = (Integer) memoria[IDX_NUM_PARTICIONES];
        
        for (int i = 0; i < numParticiones; i++) {
            int base = IDX_TABLA_PARTICIONES + (i * TAMANO_ENTRADA_PARTICION);
            int estado = (Integer) memoria[base + 2];
            
            if (estado == 1) { // Partición ocupada
                int numeroBCP = (Integer) memoria[base + 4];
                BCP bcp = obtenerBCP(numeroBCP);
                
                if (bcp != null) {
                    int tamanoParticion = (Integer) memoria[base + 1];
                    int tamanoProceso = bcp.getTamanoProceso();
                    fragmentacion += (tamanoParticion - tamanoProceso);
                }
            }
        }
        
        return fragmentacion;
    }
    
    public String generarReporte() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== MEMORIA PRINCIPAL - PARTICIONAMIENTO FIJO ==========\n");
        sb.append(String.format("Tamaño total: %d KB\n", TAMANO_TOTAL));
        sb.append(String.format("Área SO: %d KB\n", TAMANO_SO));
        sb.append(String.format("Área Usuario: %d KB\n", TAMANO_USUARIO));
        sb.append(String.format("Tipo: %s\n", getTipoParticionamiento()));
        sb.append(String.format("Número de particiones: %d\n", getNumeroParticiones()));
        sb.append(String.format("BCPs activos: %d/%d\n", getCantidadBCPsActivos(), MAX_PROCESOS));
        sb.append(String.format("Espacio libre: %d KB\n", getEspacioLibreTotal()));
        sb.append(String.format("Fragmentación interna total: %d KB\n", getFragmentacionInternaTotal()));
        sb.append("\n=== TABLA DE PARTICIONES ===\n");
        
        int numParticiones = (Integer) memoria[IDX_NUM_PARTICIONES];
        for (int i = 0; i < numParticiones; i++) {
            sb.append(getInfoParticion(i)).append("\n");
        }
        
        sb.append("===============================================================\n");
        return sb.toString();
    }
}