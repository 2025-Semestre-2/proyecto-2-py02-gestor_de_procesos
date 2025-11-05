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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import so.memoria.estrategias.EstrategiaParticionamientoDinamico;
import so.memoria.estrategias.EstrategiaParticionamientoFijo;

/**
 * Clase principal del Sistema Operativo simulado.
 * Coordina la ejecución de múltiples CPUs y la gestión de procesos.
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
    private final ExecutorService executorService;
    private volatile boolean sistemaActivo;
    private final Map<Integer, ProcesoInfo> informacionProcesos;
    private final List<EstadisticasProceso> estadisticasCompletados;
    private final Random random;
    
    // ========== COLAS DE CONTROL ==========
    private final Queue<String> programasPendientes;
    private final Map<Integer, Integer> distribucionProcesos; // CPU -> cantidad procesos
    
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
     * Constructor del Sistema Operativo
     * @param tamanoMemSecundaria
     * @param tamanoMemVirtual
     * @param tamanoMemUsuario
     * @param tipoEstrategia
     * @param configEstrategia
     * @param cantidadCPUs
     * @param algoritmosPlanificacion
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
        this.executorService = Executors.newFixedThreadPool(cantidadCPUs);
        this.sistemaActivo = false;
        this.informacionProcesos = new HashMap<>();
        this.estadisticasCompletados = new ArrayList<>();
        this.random = new Random();
        this.programasPendientes = new LinkedList<>();
        this.distribucionProcesos = new HashMap<>();
        
        // Inicializar distribución de CPUs
        for (int i = 0; i < cantidadCPUs; i++) {
            distribucionProcesos.put(i, 0);
        }
        
        System.out.println("[SISTEMA OPERATIVO] Inicializado con " + cantidadCPUs + " CPUs");
    }
    
    // ========== GESTIÓN DE ARCHIVOS Y PROGRAMAS ==========
    
    /**
     * Carga archivos a memoria secundaria
     * 
     * @param nombres nombres de los programas
     * @param programas contenido de los programas (líneas de código)
     * @return true si se cargaron correctamente
     */
    public boolean cargarArchivosMemoriaSecundaria(String[] nombres, List<String>[] programas) {
        try {
            memoriaSecundaria.cargarProgramas(nombres, programas);
            
            // Agregar a la cola de programas pendientes
            programasPendientes.addAll(Arrays.asList(nombres));
            
            System.out.println("[SO] programas cargados a memoria secundaria");
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
                // Implementar para estrategia variable si existe
                // return new EstrategiaParticionamientoVariable();
                throw new UnsupportedOperationException("Estrategia VARIABLE no implementada aún");
                
            case "PAGINACION":
                // Implementar para paginación si existe
                // return new EstrategiaPaginacion();
                throw new UnsupportedOperationException("Estrategia PAGINACION no implementada aún");
                
            default:
                throw new IllegalArgumentException("Tipo de estrategia no válido: " + tipoEstrategia);
        }
    }
    
    /**
     * Carga programas a memoria principal distribuidos entre las CPUs
     * 
     * @return número de programas cargados exitosamente
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
                        // Continuar con las siguientes instrucciones
                    }
                }
                
                if (instruccionesValidas.isEmpty()) {
                    System.err.println("[SO] No hay instrucciones válidas en: " + nombrePrograma);
                    continue;
                }
                
                // Seleccionar CPU para distribución balanceada
                int cpuSeleccionado = seleccionarCPUBalanceado();
                
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
                
                // Agregar a cola de trabajos
                memoriaPrincipal.encolarTrabajo(numeroBCP);
                
                // Registrar información del proceso
                int tiempoLlegada = calcularTiempoLlegada(cpuSeleccionado);
                ProcesoInfo info = new ProcesoInfo(
                    idProceso, nombrePrograma, cpuSeleccionado, 
                    tiempoLlegada, instruccionesValidas.size(), System.currentTimeMillis()
                );
                info.estado = EstadoProceso.LISTO;
                informacionProcesos.put(idProceso, info);
                
                // Notificar a planificadores
                planificadores[cpuSeleccionado].onProcesoAgregado(bcp);
                
                // Actualizar distribución
                distribucionProcesos.put(cpuSeleccionado, distribucionProcesos.get(cpuSeleccionado) + 1);
                
                programasCargados++;
                System.out.println("[SO] Programa cargado: " + nombrePrograma + 
                                 " → CPU " + cpuSeleccionado + " (Instrucciones: " + instruccionesValidas.size() + ")");
                
            } catch (Exception e) {
                System.err.println("[SO] Error al cargar programa " + nombrePrograma + ": " + e.getMessage());
                programasPendientes.add(nombrePrograma); // Reintentar después
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
        int base = distribucionProcesos.get(cpu) * 3; // Base creciente
        return base + random.nextInt(3); // Random 0-2
    }
    
    // ========== EJECUCIÓN DEL SISTEMA ==========
    
    /**
     * Inicia el sistema operativo
     */
    public void iniciar() {
        if (sistemaActivo) {
            System.out.println("[SO] Sistema ya está activo");
            return;
        }
        
        sistemaActivo = true;
        System.out.println("[SO] Sistema operativo iniciado");
        
        // Iniciar ejecución en cada CPU
        for (int cpu = 0; cpu < cantidadCPUs; cpu++) {
            final int cpuId = cpu;
            executorService.submit(() -> ejecutarCPU(cpuId));
        }
    }
    
    /**
     * Detiene el sistema operativo
     */
    public void detener() {
        sistemaActivo = false;
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("[SO] Sistema operativo detenido");
    }
    
    /**
     * Ciclo de ejecución para cada CPU
     */
    private void ejecutarCPU(int cpuId) {
        System.out.println("[CPU " + cpuId + "] Iniciando ejecución");
        
        while (sistemaActivo) {
            try {
                // Seleccionar siguiente proceso a ejecutar
                int numeroBCP = planificadores[cpuId].seleccionarSiguiente(memoriaPrincipal);
                
                if (numeroBCP >= 0) {
                    // Despachar proceso
                    despachador.despachar(numeroBCP);
                    BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);
                    
                    if (bcp != null) {
                        // Actualizar información del proceso
                        actualizarInfoProceso(bcp, EstadoProceso.EJECUCION, cpuId);
                        
                        // Ejecutar siguiente instrucción
                        boolean continuar = ejecutores[cpuId].ejecutarSiguiente();
                        
                        if (!continuar || bcp.getEstado() == EstadoProceso.FINALIZADO) {
                            // Proceso terminado
                            manejarProcesoTerminado(bcp, numeroBCP, cpuId);
                        } else {
                            // Proceso continúa, devolver a cola de listos
                            bcp.setEstado(EstadoProceso.LISTO);
                            memoriaPrincipal.actualizarBCP(numeroBCP, bcp);
                            memoriaPrincipal.encolarListo(numeroBCP);
                            actualizarInfoProceso(bcp, EstadoProceso.LISTO, cpuId);
                        }
                    }
                } else {
                    // No hay procesos listos, intentar cargar más
                    if (memoriaPrincipal.colaTrabajosVacia() && programasPendientes.isEmpty()) {
                        // No hay más trabajo, pausar brevemente
                        Thread.sleep(100);
                    } else if (memoriaPrincipal.colaListosVacia()) {
                        // Cargar más procesos si es posible
                        cargarProgramasMemoriaPrincipal();
                    }
                }
                
                // Pequeña pausa para evitar uso excesivo de CPU
                Thread.sleep(10);
                
            } catch (Exception e) {
                System.err.println("[CPU " + cpuId + "] Error en ejecución: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("[CPU " + cpuId + "] Ejecución finalizada");
    }
    
    /**
     * Maneja la finalización de un proceso
     */
    private void manejarProcesoTerminado(BCP bcp, int numeroBCP, int cpuId) {
        try {
            // Actualizar información
            actualizarInfoProceso(bcp, EstadoProceso.FINALIZADO, cpuId);
            
            // Crear estadísticas
            EstadisticasProceso estadisticas = new EstadisticasProceso(
                bcp.getIdProceso(),
                bcp.getNombreProceso(),
                (long) informacionProcesos.get(bcp.getIdProceso()).tiempoInicio,
                bcp.getTiempoCPUUsado(),
                bcp.getTamanoProceso(),
                EstadoProceso.FINALIZADO
            );
            estadisticasCompletados.add(estadisticas);
            
            // Liberar recursos
            memoriaPrincipal.liberarBCP(numeroBCP);
            informacionProcesos.remove(bcp.getIdProceso());
            
            // Actualizar distribución
            distribucionProcesos.put(cpuId, distribucionProcesos.get(cpuId) - 1);
            
            // Notificar planificador
            planificadores[cpuId].onProcesoFinalizado(bcp);
            
            System.out.println("[CPU " + cpuId + "] Proceso finalizado: " + bcp.getNombreProceso());
            
            // Cargar nuevo proceso si hay pendientes
            if (!programasPendientes.isEmpty()) {
                cargarProgramasMemoriaPrincipal();
            }
            
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
     * Obtiene el estado actual del sistema
     */
    public Map<String, Object> getEstadoSistema() {
        Map<String, Object> estado = new HashMap<>();
        estado.put("activo", sistemaActivo);
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
    
    /**
     * Ejecuta una sola instrucción en todos los CPUs (para modo paso a paso)
     */
    public void ejecutarInstruccionPaso() {
        if (!sistemaActivo) {
            for (int cpu = 0; cpu < cantidadCPUs; cpu++) {
                try {
                    int numeroBCP = planificadores[cpu].seleccionarSiguiente(memoriaPrincipal);
                    if (numeroBCP >= 0) {
                        despachador.despachar(numeroBCP);
                        ejecutores[cpu].ejecutarSiguiente();
                    }
                } catch (Exception e) {
                    System.err.println("[CPU " + cpu + "] Error en ejecución paso: " + e.getMessage());
                }
            }
        }
    }
    
    // ========== GETTERS ==========
    
    public boolean isSistemaActivo() {
        return sistemaActivo;
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