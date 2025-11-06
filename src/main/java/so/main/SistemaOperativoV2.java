package so.main;

import so.memoria.MemoriaPrincipalV2;
import so.memoria.MemoriaSecundaria;
import so.memoria.estrategias.IEstrategiaParticionamiento;
import so.instrucciones.EjecutorInstrucciones;
import so.instrucciones.Instruccion;
import so.instrucciones.InstruccionParser;
import so.gestordeprocesos.Despachador;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.EstadoProceso;
import so.planificacion.IPlanificador;
import so.estadisticas.EstadisticasProceso;

import java.util.*;
import so.memoria.estrategias.EstrategiaParticionamientoDinamico;
import so.memoria.estrategias.EstrategiaParticionamientoFijo;

/**
 * Clase principal del Sistema Operativo simulado.
 * Coordina la ejecución de múltiples CPUs y la gestión de procesos.
 * ACTUALIZADO: Soporte para ejecución automática y paso a paso
 * 
 * @author dylan
 */
public class SistemaOperativoV2 {
    
    // ========== CONFIGURACIÓN DEL SISTEMA ==========
    private final int tamanoMemoriaSecundaria;
    private final int tamanoMemoriaVirtual;
    private final int tamanoMemoriaUsuario;
    private final IEstrategiaParticionamiento estrategiaMemoria;
    private final int cantidadCPUs;
    
    // ========== COMPONENTES DEL SISTEMA ==========
    private final MemoriaSecundaria memoriaSecundaria;
    private final MemoriaPrincipalV2 memoriaPrincipal;
    private final Despachador despachador;
    private final EjecutorInstrucciones[] ejecutores;
    private final IPlanificador[] planificadores;
    
    // ========== CONTROL Y ESTADÍSTICAS ==========
    private volatile boolean sistemaActivo;
    private volatile boolean ejecucionPausada;
    private final Map<Integer, ProcesoInfo> informacionProcesos;
    private final List<EstadisticasProceso> estadisticasCompletados;
    private final Random random;
    
    // ========== COLAS DE CONTROL ==========
    private final Queue<String> programasPendientes;
    private final Map<Integer, Integer> distribucionProcesos; // CPU -> cantidad procesos
    private final Map<Integer, Integer> cpuProcesoActual; // CPU -> numeroBCP actual
    
    // ========== LISTENERS PARA GUI ==========
    private final List<SistemaListener> listeners;
    
    /**
     * Información de proceso para la interfaz gráfica
     */
    public static class ProcesoInfo {
        public final int idProceso;
        public final String nombre;
        public final int cpuAsignado;
        public final int tiempoLlegada;
        public final int rafaga;
        public int tiempoRestante;
        public EstadoProceso estado;
        public final double tiempoInicio;
        
        public ProcesoInfo(int id, String nombre, int cpu, int llegada, int rafaga, double inicio) {
            this.idProceso = id;
            this.nombre = nombre;
            this.cpuAsignado = cpu;
            this.tiempoLlegada = llegada;
            this.rafaga = rafaga;
            this.tiempoRestante = rafaga;
            this.estado = EstadoProceso.NUEVO;
            this.tiempoInicio = inicio;
        }
    }
    
    /**
     * Interface para listeners de eventos del sistema
     */
    public interface SistemaListener {
        void onProcesoEjecutado(int cpu, BCP bcp);
        void onProcesoFinalizado(int cpu, BCP bcp);
        void onProcesoNuevo(BCP bcp);
        void onEstadoCambiado(boolean activo, boolean pausado);
    }
    
    /**
     * Constructor del Sistema Operativo
     */
    public SistemaOperativoV2(int tamanoMemSecundaria, int tamanoMemVirtual,
                          int tamanoMemUsuario, String tipoEstrategia, Object configEstrategia, 
                          int cantidadCPUs, IPlanificador[] algoritmosPlanificacion) {
        
        // Validación de parámetros
        if (cantidadCPUs <= 0) {
            throw new IllegalArgumentException("Debe haber al menos 1 CPU");
        }
        if (algoritmosPlanificacion == null || algoritmosPlanificacion.length != cantidadCPUs) {
            throw new IllegalArgumentException("Debe proporcionar un algoritmo de planificación por CPU");
        }
        
        // Configuración
        this.tamanoMemoriaSecundaria = tamanoMemSecundaria;
        this.tamanoMemoriaVirtual = tamanoMemVirtual;
        this.tamanoMemoriaUsuario = tamanoMemUsuario;
        this.estrategiaMemoria = crearEstrategiaMemoria(tipoEstrategia, configEstrategia);
        this.cantidadCPUs = cantidadCPUs;
        this.planificadores = algoritmosPlanificacion;
        
        // Inicializar componentes
        this.memoriaSecundaria = new MemoriaSecundaria(tamanoMemSecundaria, tamanoMemVirtual);
        this.memoriaPrincipal = new MemoriaPrincipalV2(estrategiaMemoria, tamanoMemUsuario);
        this.despachador = new Despachador(memoriaPrincipal);
        
        // Inicializar ejecutores (uno por CPU)
        this.ejecutores = new EjecutorInstrucciones[cantidadCPUs];
        for (int i = 0; i < cantidadCPUs; i++) {
            this.ejecutores[i] = new EjecutorInstrucciones(memoriaPrincipal, despachador);
        }
        
        // Inicializar estructuras de control
        this.sistemaActivo = false;
        this.ejecucionPausada = false;
        this.informacionProcesos = Collections.synchronizedMap(new HashMap<>());
        this.estadisticasCompletados = Collections.synchronizedList(new ArrayList<>());
        this.random = new Random();
        this.programasPendientes = new LinkedList<>();
        this.distribucionProcesos = new HashMap<>();
        this.cpuProcesoActual = new HashMap<>();
        this.listeners = new ArrayList<>();
        
        // Inicializar distribución de CPUs
        for (int i = 0; i < cantidadCPUs; i++) {
            distribucionProcesos.put(i, 0);
            cpuProcesoActual.put(i, -1);
        }
        
        System.out.println("[SISTEMA OPERATIVO] Inicializado con " + cantidadCPUs + " CPUs");
    }
    
    // ========== GESTIÓN DE LISTENERS ==========
    
    public void addListener(SistemaListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(SistemaListener listener) {
        listeners.remove(listener);
    }
    
    private void notificarProcesoEjecutado(int cpu, BCP bcp) {
        for (SistemaListener listener : listeners) {
            listener.onProcesoEjecutado(cpu, bcp);
        }
    }
    
    private void notificarProcesoFinalizado(int cpu, BCP bcp) {
        for (SistemaListener listener : listeners) {
            listener.onProcesoFinalizado(cpu, bcp);
        }
    }
    
    private void notificarProcesoNuevo(BCP bcp) {
        for (SistemaListener listener : listeners) {
            listener.onProcesoNuevo(bcp);
        }
    }
    
    private void notificarEstadoCambiado() {
        for (SistemaListener listener : listeners) {
            listener.onEstadoCambiado(sistemaActivo, ejecucionPausada);
        }
    }
    
    // ========== GESTIÓN DE ARCHIVOS Y PROGRAMAS ==========
    
    /**
     * Carga archivos a memoria secundaria
     */
    public boolean cargarArchivosMemoriaSecundaria(String[] nombres, List<String>[] programas) {
        try {
            memoriaSecundaria.cargarProgramas(nombres, programas);
            
            // Agregar a la cola de programas pendientes
            programasPendientes.addAll(Arrays.asList(nombres));
            
            System.out.println("[SO] " + nombres.length + " programas cargados a memoria secundaria");
            return true;
            
        } catch (Exception e) {
            System.err.println("[SO] Error al cargar archivos: " + e.getMessage());
            return false;
        }
    }

    /**
     * Crea la estrategia de memoria según la configuración
     */
    private IEstrategiaParticionamiento crearEstrategiaMemoria(String tipoEstrategia, Object config) {
        switch (tipoEstrategia.toUpperCase()) {
            case "FIJO_IGUAL":
                if (config instanceof Integer) {
                    int tamanoParticion = (Integer) config;
                    return new EstrategiaParticionamientoFijo(tamanoParticion);
                } else {
                    throw new IllegalArgumentException("Para FIJO_IGUAL se requiere un Integer con el tamaño de partición");
                }
                
            case "FIJO_DESIGUAL":
                return new EstrategiaParticionamientoFijo(EstrategiaParticionamientoFijo.TipoParticionamiento.DESIGUAL);

            case "DINAMICO":
                return new EstrategiaParticionamientoDinamico();
                
            case "VARIABLE":
                throw new UnsupportedOperationException("Estrategia VARIABLE no implementada aún");
                
            case "PAGINACION":
                throw new UnsupportedOperationException("Estrategia PAGINACION no implementada aún");
                
            default:
                throw new IllegalArgumentException("Tipo de estrategia no válido: " + tipoEstrategia);
        }
    }
    
    /**
     * Carga programas a memoria principal distribuidos entre las CPUs
     */
    public int cargarProgramasMemoriaPrincipal() {
        int programasCargados = 0;
        
        while (!programasPendientes.isEmpty() && memoriaPrincipal.getCantidadBCPsActivos() < memoriaPrincipal.getMaxProcesos()) {
            String nombrePrograma = programasPendientes.poll();
            if (nombrePrograma == null) continue;
            
            try {
                // Leer programa de memoria secundaria
                List<String> lineasPrograma = memoriaSecundaria.leerPrograma(nombrePrograma);
                if (lineasPrograma == null || lineasPrograma.isEmpty()) {
                    System.err.println("[SO] Programa no encontrado: " + nombrePrograma);
                    continue;
                }
                
                // Parsear y validar instrucciones
                List<Instruccion> instruccionesValidas = new ArrayList<>();
                for (String linea : lineasPrograma) {
                    try {
                        Instruccion instruccion = InstruccionParser.parse(linea);
                        if (instruccion != null) {
                            instruccionesValidas.add(instruccion);
                        }
                    } catch (Exception e) {
                        System.err.println("[SO] Instrucción inválida en " + nombrePrograma + ": " + linea);
                    }
                }
                
                if (instruccionesValidas.isEmpty()) {
                    System.err.println("[SO] No hay instrucciones válidas en: " + nombrePrograma);
                    continue;
                }
                
                // Seleccionar CPU para distribución balanceada
                int cpuSeleccionado = seleccionarCPUBalanceado();

                int procesosEnCPU = distribucionProcesos.get(cpuSeleccionado);

                // Límite de 5 procesos por CPU
                if (procesosEnCPU >= 5) {
                    System.out.println("[SO] CPU " + cpuSeleccionado + " alcanzó límite de 5 procesos");
                    continue; // Saltar este CPU, intentar con otro
                }
                
                // Cargar instrucciones a memoria principal
                Instruccion[] arrayInstrucciones = instruccionesValidas.toArray(new Instruccion[0]);
                var infoAsignacion = memoriaPrincipal.cargarInstrucciones(arrayInstrucciones);
                
                if (infoAsignacion == null) {
                    System.err.println("[SO] No hay espacio en memoria principal para: " + nombrePrograma);
                    programasPendientes.add(nombrePrograma); // Reintentar después
                    continue;
                }
                
                // Crear BCP
                int idProceso = memoriaPrincipal.generarNuevoIDProceso();
                BCP bcp = new BCP(idProceso, nombrePrograma, infoAsignacion.direccionBase, instruccionesValidas.size());                
                
                // Asignar a memoria principal
                int numeroBCP = memoriaPrincipal.crearBCP(bcp);
                if (numeroBCP < 0) {
                    System.err.println("[SO] No se pudo crear BCP para: " + nombrePrograma);
                    continue;
                }
                
                // Asociar asignación de memoria al proceso
                memoriaPrincipal.asociarAsignacionAProceso(bcp, infoAsignacion, numeroBCP);
                
                // Cambiar estado a LISTO y encolar
                bcp.setEstado(EstadoProceso.LISTO);
                memoriaPrincipal.actualizarBCP(numeroBCP, bcp);
                memoriaPrincipal.encolarListo(numeroBCP);
                
                // Registrar información del proceso
                int tiempoLlegada = calcularTiempoLlegada(cpuSeleccionado);
                ProcesoInfo info = new ProcesoInfo(
                    idProceso, nombrePrograma, cpuSeleccionado, 
                    tiempoLlegada, instruccionesValidas.size(), System.currentTimeMillis()
                );
                bcp.setTiempoLlegadaProgramado(tiempoLlegada);
                info.estado = EstadoProceso.LISTO;
                informacionProcesos.put(idProceso, info);
                
                // Notificar a planificadores
                planificadores[cpuSeleccionado].onProcesoAgregado(bcp);
                
                // Actualizar distribución
                distribucionProcesos.put(cpuSeleccionado, distribucionProcesos.get(cpuSeleccionado) + 1);
                
                // Notificar listeners
                notificarProcesoNuevo(bcp);
                
                programasCargados++;
                System.out.println("[SO] Programa cargado: " + nombrePrograma + 
                                 " → CPU " + cpuSeleccionado + " (Instrucciones: " + instruccionesValidas.size() + ")");
                
            } catch (Exception e) {
                System.err.println("[SO] Error al cargar programa " + nombrePrograma + ": " + e.getMessage());
                programasPendientes.add(nombrePrograma);
            }
        }
        
        System.out.println("[SO] " + programasCargados + " programas cargados a memoria principal");
        return programasCargados;
    }
    
    /**
     * Selecciona la CPU con menos procesos para distribución balanceada
     */
    private int seleccionarCPUBalanceado() {
        int cpuMenosCargado = 0;
        int minProcesos = Integer.MAX_VALUE;
        
        for (int i = 0; i < cantidadCPUs; i++) {
            int procesos = distribucionProcesos.get(i);
            if (procesos < minProcesos) {
                minProcesos = procesos;
                cpuMenosCargado = i;
            }
        }
        
        return cpuMenosCargado;
    }
    
    /**
     * Calcula tiempo de llegada con random 0-2
     */
    private int calcularTiempoLlegada(int cpu) {
        int base = distribucionProcesos.get(cpu) * 3;
        return base + random.nextInt(3);
    }
    
    // ========== EJECUCIÓN DEL SISTEMA - NUEVOS MÉTODOS ==========
    
    /**
     * Ejecuta UNA instrucción por cada CPU (modo paso a paso)
     * @return true si se ejecutó al menos una instrucción
     */
    public synchronized boolean ejecutarPasoAPaso() {        
        boolean seEjecutoAlgo = false;
        
        for (int cpu = 0; cpu < cantidadCPUs; cpu++) {
            if (ejecutarInstruccionEnCPU(cpu)) {
                seEjecutoAlgo = true;
            }
        }
        
        // Intentar cargar más procesos si hay pendientes
        if (!programasPendientes.isEmpty()) {
            cargarProgramasMemoriaPrincipal();
        }
        
        return seEjecutoAlgo;
    }
    
    /**
     * Ejecuta UNA instrucción en un CPU específico
     * @param cpuId ID del CPU (0-4)
     * @return true si se ejecutó una instrucción
     */
    private synchronized boolean ejecutarInstruccionEnCPU(int cpuId) {
        try {            
            // 1. VERIFICAR PROCESO ACTUAL
            int procesoActual = cpuProcesoActual.get(cpuId);
            if (procesoActual >= 0) {
                BCP bcpActual = memoriaPrincipal.obtenerBCP(procesoActual);
                ProcesoInfo info = informacionProcesos.get(bcpActual.getIdProceso());
                if (info != null) {
                    info.tiempoRestante--;
                }

                int[] colaListos = memoriaPrincipal.obtenerColaListos();
                for (int numeroBCP : colaListos) {
                    BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);
                    if (bcp != null && bcp.getEstado() == EstadoProceso.LISTO) {
                        bcp.incrementarTiempoEspera();
                        memoriaPrincipal.actualizarBCP(numeroBCP, bcp);
                    }
                }
                
                if (bcpActual != null && bcpActual.getEstado() == EstadoProceso.EJECUCION) {
                    // Continuar ejecutando el proceso actual según el algoritmo
                    boolean continuar = ejecutores[cpuId].ejecutarSiguiente();
                    bcpActual = memoriaPrincipal.obtenerBCP(procesoActual);

                    if (!continuar || bcpActual.getEstado() == EstadoProceso.FINALIZADO) {
                        manejarProcesoTerminado(bcpActual, procesoActual, cpuId);
                        cpuProcesoActual.put(cpuId, -1);
                    }
                    return true;
                }
            }

            // 2. SELECCIONAR NUEVO PROCESO SOLO SI NO HAY UNO EN EJECUCIÓN
            int numeroBCP = planificadores[cpuId].seleccionarSiguiente(memoriaPrincipal);
            if (numeroBCP < 0) {
                return false;
            }

            // 3. DESPACHAR NUEVO PROCESO
            cpuProcesoActual.put(cpuId, numeroBCP);
            despachador.despachar(numeroBCP);
            BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);

            // 4. ACTUALIZAR ESTADO INMEDIATAMENTE
            actualizarInfoProceso(bcp, EstadoProceso.EJECUCION, cpuId);

            // 5. EJECUTAR PRIMERA INSTRUCCIÓN
            boolean continuar = ejecutores[cpuId].ejecutarSiguiente();
            bcp = memoriaPrincipal.obtenerBCP(numeroBCP);

            notificarProcesoEjecutado(cpuId, bcp);

            if (!continuar || bcp.getEstado() == EstadoProceso.FINALIZADO) {
                manejarProcesoTerminado(bcp, numeroBCP, cpuId);
                cpuProcesoActual.put(cpuId, -1);
            }

            return true;

        } catch (Exception e) {
            System.err.println("[CPU " + cpuId + "] Error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Inicia ejecución automática
     */
    public synchronized void iniciarEjecucionAutomatica() {        
        sistemaActivo = true;
        ejecucionPausada = false;
        notificarEstadoCambiado();
        System.out.println("[SO] Ejecución automática iniciada");
    }
    
    /**
     * Pausa la ejecución automática (mantiene el contexto)
     */
    public synchronized void pausarEjecucionAutomatica() {        
        sistemaActivo = false;
        ejecucionPausada = true;
        
        // Guardar contexto de todos los procesos en ejecución
        for (int cpu = 0; cpu < cantidadCPUs; cpu++) {
            int numeroBCP = cpuProcesoActual.get(cpu);
            if (numeroBCP >= 0) {
                BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);
                if (bcp != null && bcp.getEstado() == EstadoProceso.EJECUCION) {
                    // Mantener el contexto guardado en el BCP
                    ejecutores[cpu].getCPU().guardarContexto(bcp);
                    memoriaPrincipal.actualizarBCP(numeroBCP, bcp);
                }
            }
        }
        
        notificarEstadoCambiado();
        System.out.println("[SO] Ejecución automática pausada - Contexto guardado");
    }
    
    /**
     * Detiene completamente la ejecución automática
     */
    public synchronized void detenerEjecucionAutomatica() {
        sistemaActivo = false;
        ejecucionPausada = false;
        
        // Detener todos los procesos en ejecución
        for (int cpu = 0; cpu < cantidadCPUs; cpu++) {
            cpuProcesoActual.put(cpu, -1);
        }
        
        despachador.detener();
        notificarEstadoCambiado();
        System.out.println("[SO] Ejecución automática detenida");
    }
    
    /**
     * Maneja la finalización de un proceso
     */
    private void manejarProcesoTerminado(BCP bcp, int numeroBCP, int cpuId) {
        try {
            // 1. ACTUALIZAR ESTADO A FINALIZADO
            bcp.setEstado(EstadoProceso.FINALIZADO);
            memoriaPrincipal.actualizarBCP(numeroBCP, bcp);
            actualizarInfoProceso(bcp, EstadoProceso.FINALIZADO, cpuId);

            // 2. CREAR ESTADÍSTICAS (NO ELIMINAR DE informacionProcesos)
            ProcesoInfo info = informacionProcesos.get(bcp.getIdProceso());
            if (info != null) {
                EstadisticasProceso estadisticas = new EstadisticasProceso(
                    bcp.getIdProceso(),
                    bcp.getNombreProceso(),
                    (long) info.tiempoInicio,
                    bcp.getTiempoCPUUsado(),
                    bcp.getTamanoProceso(),
                    EstadoProceso.FINALIZADO
                );
                estadisticasCompletados.add(estadisticas);

                // ACTUALIZAR INFO PERO NO ELIMINAR
                info.estado = EstadoProceso.FINALIZADO;
                info.tiempoRestante = 0;
            }

            // 3. LIBERAR MEMORIA DEL PROCESO
            memoriaPrincipal.liberarBCP(numeroBCP);

            // 4. ACTUALIZAR DISTRIBUCIÓN
            distribucionProcesos.put(cpuId, distribucionProcesos.get(cpuId) - 1);

            if (!programasPendientes.isEmpty() && memoriaPrincipal.getCantidadBCPsActivos() < memoriaPrincipal.getMaxProcesos()) {
                int programasCargados = cargarProgramasMemoriaPrincipal();
                if (programasCargados > 0) {
                    System.out.println("[SO] " + programasCargados + " nuevos procesos cargados después de finalizar " + bcp.getNombreProceso());
                }
            }            
            
            // 5. NOTIFICAR
            planificadores[cpuId].onProcesoFinalizado(bcp);
            notificarProcesoFinalizado(cpuId, bcp);

            System.out.println("[CPU " + cpuId + "] Proceso finalizado: " + bcp.getNombreProceso());

        } catch (Exception e) {
            System.err.println("[SO] Error al manejar proceso terminado: " + e.getMessage());
        }
    }
    
    /**
     * Actualiza la información de un proceso para la interfaz
     */
    private void actualizarInfoProceso(BCP bcp, EstadoProceso estado, int cpuId) {
        ProcesoInfo info = informacionProcesos.get(bcp.getIdProceso());
        if (info != null) {
            info.estado = estado;
            info.tiempoRestante = bcp.getTamanoProceso() - bcp.getPC();
        }
    }
    
    // ========== MÉTODOS DE CONSULTA PARA INTERFAZ ==========
    
    /**
     * Obtiene el BCP actualmente en ejecución en un CPU
     */
    public BCP getBCPEnCPU(int cpuId) {
        if (cpuId < 0 || cpuId >= cantidadCPUs) {
            return null;
        }
        
        int numeroBCP = cpuProcesoActual.get(cpuId);
        if (numeroBCP < 0) {
            return null;
        }
        
        return memoriaPrincipal.obtenerBCP(numeroBCP);
    }
    
    /**
     * Obtiene el ejecutor de un CPU específico
     */
    public EjecutorInstrucciones getEjecutor(int cpuId) {
        if (cpuId < 0 || cpuId >= cantidadCPUs) {
            return null;
        }
        return ejecutores[cpuId];
    }
    
    /**
     * Obtiene información de todos los procesos para mostrar en tabla
     */
    public List<ProcesoInfo> getInformacionProcesos() {
        return new ArrayList<>(informacionProcesos.values());
    }
    
    /**
     * Obtiene las estadísticas de procesos completados
     */
    public List<EstadisticasProceso> getEstadisticasCompletados() {
        return new ArrayList<>(estadisticasCompletados);
    }

    /**
     * Obtiene información de procesos para mostrar en tabla
     */
    public List<Object[]> getInformacionProcesosParaTabla() {
        List<Object[]> datos = new ArrayList<>();

        for (ProcesoInfo info : informacionProcesos.values()) {
            Object[] fila = new Object[]{
                info.nombre,
                info.rafaga,
                info.tiempoLlegada,
                info.cpuAsignado,
                info.estado.toString(),
                info.tiempoRestante
            };
            datos.add(fila);
        }

        return datos;
    }

    /**
     * Obtiene información específica de un proceso
     */
    public ProcesoInfo getInfoProceso(int idProceso) {
        return informacionProcesos.get(idProceso);
    }

    /**
     * Indica si aún hay procesos por ejecutar en el sistema.
     * Retorna true si hay procesos en memoria principal o en la cola de pendientes.
     */
    public boolean hayProcesosPorEjecutar() {
        // Hay procesos cargados en memoria principal (activos o listos)
        boolean hayActivos = memoriaPrincipal.getCantidadBCPsActivos() > 0;

        // Hay procesos pendientes en memoria secundaria
        boolean hayPendientes = !programasPendientes.isEmpty();

        // Si cualquiera de los dos es cierto, aún hay trabajo por ejecutar
        return hayActivos || hayPendientes;
    }    
    
    /**
     * Obtiene el estado actual del sistema
     */
    public Map<String, Object> getEstadoSistema() {
        Map<String, Object> estado = new HashMap<>();
        estado.put("activo", sistemaActivo);
        estado.put("pausado", ejecucionPausada);
        estado.put("cpus", cantidadCPUs);
        estado.put("procesosActivos", memoriaPrincipal.getCantidadBCPsActivos());
        estado.put("procesosPendientes", programasPendientes.size());
        estado.put("procesosCompletados", estadisticasCompletados.size());
        estado.put("memoriaLibre", memoriaPrincipal.getEstrategia().getEspacioLibreTotal());
        
        // Estado de cada CPU
        Map<Integer, String> estadoCPUs = new HashMap<>();
        for (int i = 0; i < cantidadCPUs; i++) {
            estadoCPUs.put(i, planificadores[i].getNombre() + " - " + distribucionProcesos.get(i) + " procesos");
        }
        estado.put("estadoCPUs", estadoCPUs);
        
        return estado;
    }
    
    // ========== GETTERS ==========
    
    public boolean isSistemaActivo() {
        return sistemaActivo;
    }
    
    public boolean isEjecucionPausada() {
        return ejecucionPausada;
    }
    
    public int getCantidadCPUs() {
        return cantidadCPUs;
    }
    
    public MemoriaPrincipalV2 getMemoriaPrincipal() {
        return memoriaPrincipal;
    }
    
    public MemoriaSecundaria getMemoriaSecundaria() {
        return memoriaSecundaria;
    }
    
    public int getProcesosPendientes() {
        return programasPendientes.size();
    }

    public List<BCP> getBCPsCargados() {
        List<BCP> bcps = new ArrayList<>();
        for (int i = 0; i < memoriaPrincipal.getMaxProcesos(); i++) {
            BCP bcp = memoriaPrincipal.obtenerBCP(i);
            if (bcp != null) {
                bcps.add(bcp);
            }
        }
        return bcps;
    }
    
    public IPlanificador getPlanificador(int cpuId) {
        if (cpuId >= 0 && cpuId < cantidadCPUs) {
            return planificadores[cpuId];
        }
        return null;
    }
    
    /**
     * Genera reporte completo del sistema
     */
    public String generarReporte() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== SISTEMA OPERATIVO ==========\n");
        sb.append(String.format("Estado: %s\n", sistemaActivo ? "ACTIVO" : "INACTIVO"));
        sb.append(String.format("CPUs: %d\n", cantidadCPUs));
        sb.append(String.format("Procesos activos: %d\n", memoriaPrincipal.getCantidadBCPsActivos()));
        sb.append(String.format("Procesos pendientes: %d\n", programasPendientes.size()));
        sb.append(String.format("Procesos completados: %d\n", estadisticasCompletados.size()));
        sb.append("\n");
        
        // Información por CPU
        sb.append("--- CPUs ---\n");
        for (int i = 0; i < cantidadCPUs; i++) {
            sb.append(String.format("CPU %d: %s - %d procesos\n", 
                i, planificadores[i].getNombre(), distribucionProcesos.get(i)));
        }
        
        sb.append("\n");
        sb.append(memoriaPrincipal.generarReporte());
        
        return sb.toString();
    }
}