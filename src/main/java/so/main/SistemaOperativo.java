package so.main;

import so.cpu.CPU;
import so.estadisticas.EstadisticasProceso;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.Despachador;
import so.gestordeprocesos.EstadoProceso;
import so.instrucciones.EjecutorInstrucciones;
import so.instrucciones.Instruccion;
import so.instrucciones.InstruccionParser;
import so.memoria.MemoriaPrincipal;
import so.memoria.MemoriaSecundaria;
import so.planificacion.IPlanificador;
import so.planificacion.PlanificadorFIFO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Sistema Operativo mejorado con gestión automática de carga de procesos
 * desde memoria secundaria cuando hay espacio disponible.
 * 
 * @author dylan
 */
public class SistemaOperativo {
    
    // ========== COMPONENTES PRINCIPALES ==========
    private final MemoriaSecundaria memoriaSecundaria;
    private final MemoriaPrincipal memoriaPrincipal;
    private final Despachador despachador;
    private final EjecutorInstrucciones ejecutor;
    private IPlanificador planificador;
    
    // ========== CONTROL DE EJECUCIÓN ==========
    private boolean sistemaActivo;
    private boolean ejecucionAutomatica;
    private Timer timerEjecucion;
    
    // ========== GESTIÓN DE PROGRAMAS PENDIENTES ==========
    private final List<String> programasPendientes;
    
    // ========== ESTADÍSTICAS ==========
    private final Map<Integer, EstadisticasProceso> estadisticas;
    private final List<String> log;
    
    // ========== LISTENERS ==========
    private CargaProcesoListener cargaListener;
    
    /**
     * Interface para notificar cuando se cargan procesos
     */
    public interface CargaProcesoListener {
        void onProcesoCargado(String nombreProceso);
        void onProcesoFinalizado(String nombreProceso);
    }
    
    public SistemaOperativo() {
        this(512, 64, 512);
    }
    
    public SistemaOperativo(int tamanoMemSecundaria, int tamanoMemVirtual, int tamanoMemPrincipal) {
        this.memoriaSecundaria = new MemoriaSecundaria(tamanoMemSecundaria, tamanoMemVirtual);
        this.memoriaPrincipal = new MemoriaPrincipal(tamanoMemPrincipal);
        this.despachador = new Despachador(memoriaPrincipal);
        this.ejecutor = new EjecutorInstrucciones(memoriaPrincipal, despachador);
        this.planificador = new PlanificadorFIFO();
        
        this.sistemaActivo = false;
        this.ejecucionAutomatica = false;
        this.timerEjecucion = null;
        
        this.programasPendientes = new ArrayList<>();
        this.estadisticas = new HashMap<>();
        this.log = new ArrayList<>();
        
        registrarLog("Sistema operativo inicializado");
    }
    
    // ========== GESTIÓN DE MEMORIA SECUNDARIA ==========
    
    public boolean cargarProgramasAMemoriaSecundaria(String[] nombres, List<String>[] programas) {
        try {
            if (nombres == null || programas == null) {
                registrarLog("ERROR: Parámetros nulos al cargar programas");
                return false;
            }
            
            if (nombres.length != programas.length) {
                registrarLog("ERROR: La cantidad de nombres no coincide con la cantidad de programas");
                return false;
            }
            
            memoriaSecundaria.cargarProgramas(nombres, programas);
            registrarLog("Cargados " + nombres.length + " programa(s) en memoria secundaria");
            
            // Actualizar lista de programas pendientes
            actualizarProgramasPendientes();
            
            for (String nombre : nombres) {
                registrarLog("  - " + nombre);
            }
            
            return true;
            
        } catch (Exception e) {
            registrarLog("ERROR al cargar programas: " + e.getMessage());
            return false;
        }
    }
    
    public boolean cargarProgramaAMemoriaSecundaria(String nombre, List<String> lineasCodigo) {
        String[] nombres = {nombre};
        List<String>[] programas = new List[]{lineasCodigo};
        return cargarProgramasAMemoriaSecundaria(nombres, programas);
    }
    
    public List<String> leerProgramaDeMemoriaSecundaria(String nombrePrograma) {
        try {
            List<String> programa = memoriaSecundaria.leerPrograma(nombrePrograma);
            
            if (programa == null) {
                registrarLog("ERROR: Programa '" + nombrePrograma + "' no encontrado");
                return null;
            }
            
            registrarLog("Programa '" + nombrePrograma + "' leído de memoria secundaria (" + 
                        programa.size() + " líneas)");
            return programa;
            
        } catch (Exception e) {
            registrarLog("ERROR al leer programa: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Actualiza la lista de programas pendientes desde memoria secundaria
     */
    private void actualizarProgramasPendientes() {
        programasPendientes.clear();
        Object[] contenido = memoriaSecundaria.getAlmacenamiento();
        
        for (int i = 0; i < contenido.length; i++) {
            Object celda = contenido[i];
            if (celda instanceof String str && str.contains(";")) {
                String[] partes = str.split(";");
                if (partes.length == 3) {
                    String nombrePrograma = partes[0];
                    if (!programasPendientes.contains(nombrePrograma)) {
                        programasPendientes.add(nombrePrograma);
                    }
                }
            }
        }
    }
    
    /**
     * Obtiene la lista de programas pendientes en memoria secundaria
     */
    public List<String> getProgramasPendientes() {
        return new ArrayList<>(programasPendientes);
    }
    
    /**
     * Verifica si hay programas pendientes en memoria secundaria
     */
    public boolean hayProgramasPendientes() {
        return !programasPendientes.isEmpty();
    }
    
    // ========== GESTIÓN DE PROCESOS ==========
    
    /**
     * Carga un programa de memoria secundaria a memoria principal
     * y lo marca como cargado en la lista de pendientes
     */
    public int cargarProcesoAMemoriaPrincipal(String nombrePrograma) {
        try {
            // Verificar límite de procesos
            if (memoriaPrincipal.getCantidadBCPsActivos() >= memoriaPrincipal.getMaxProcesos()) {
                registrarLog("ERROR: No se pueden cargar más procesos (límite: " + 
                           memoriaPrincipal.getMaxProcesos() + ")");
                return -1;
            }
            
            // Leer programa de memoria secundaria
            List<String> lineasCodigo = memoriaSecundaria.leerPrograma(nombrePrograma);
            if (lineasCodigo == null) {
                registrarLog("ERROR: Programa '" + nombrePrograma + "' no encontrado en memoria secundaria");
                return -1;
            }
            
            // Parsear instrucciones
            List<Instruccion> instruccionesParsed = new ArrayList<>();
            for (String linea : lineasCodigo) {
                try {
                    Instruccion inst = InstruccionParser.parse(linea);
                    if (inst != null) {
                        instruccionesParsed.add(inst);
                    }
                } catch (Exception e) {
                    registrarLog("ERROR al parsear línea: " + linea + " - " + e.getMessage());
                }
            }
            
            if (instruccionesParsed.isEmpty()) {
                registrarLog("ERROR: No se pudieron parsear instrucciones del programa");
                return -1;
            }
            
            // Cargar instrucciones en memoria principal
            Instruccion[] instrucciones = instruccionesParsed.toArray(new Instruccion[0]);
            int direccionBase = memoriaPrincipal.cargarInstrucciones(instrucciones);
            
            if (direccionBase < 0) {
                registrarLog("ERROR: No hay espacio suficiente en memoria principal");
                return -1;
            }
            
            // Generar ID único
            int idProceso = memoriaPrincipal.generarNuevoIDProceso();
            
            // Crear BCP
            BCP bcp = new BCP(idProceso, nombrePrograma, direccionBase, instrucciones.length);
            bcp.setEstado(EstadoProceso.NUEVO);
            
            // Guardar BCP en memoria
            int numeroBCP = memoriaPrincipal.crearBCP(bcp);
            
            if (numeroBCP < 0) {
                memoriaPrincipal.liberarInstrucciones(direccionBase, instrucciones.length);
                registrarLog("ERROR: No se pudo crear BCP para el proceso");
                return -1;
            }
            
            // Encolar en cola de trabajos
            memoriaPrincipal.encolarTrabajo(numeroBCP);
            
            // Remover de la lista de pendientes
            programasPendientes.remove(nombrePrograma);
            
            registrarLog("Proceso '" + nombrePrograma + "' (ID: " + idProceso + 
                        ") cargado en memoria principal");
            registrarLog("  - Dirección base: " + direccionBase);
            registrarLog("  - Tamaño: " + instrucciones.length + " instrucciones");
            registrarLog("  - BCP #: " + numeroBCP);
            
            // Notificar listener
            if (cargaListener != null) {
                cargaListener.onProcesoCargado(nombrePrograma);
            }
            
            return idProceso;
            
        } catch (Exception e) {
            registrarLog("ERROR al cargar proceso: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    
    /**
     * Intenta cargar procesos pendientes cuando hay espacio disponible
     * @return cantidad de procesos cargados
     */
    public int cargarProcesosPendientes() {
        if (programasPendientes.isEmpty()) {
            return 0;
        }
        
        int espaciosDisponibles = memoriaPrincipal.getMaxProcesos() - 
                                 memoriaPrincipal.getCantidadBCPsActivos();
        
        if (espaciosDisponibles <= 0) {
            return 0;
        }
        
        int cargados = 0;
        List<String> programasACargar = new ArrayList<>();
        
        // Tomar hasta el número de espacios disponibles
        for (int i = 0; i < Math.min(espaciosDisponibles, programasPendientes.size()); i++) {
            programasACargar.add(programasPendientes.get(i));
        }
        
        for (String programa : programasACargar) {
            int id = cargarProcesoAMemoriaPrincipal(programa);
            if (id > 0) {
                cargados++;
            }
        }
        
        if (cargados > 0) {
            registrarLog("✓ Se cargaron " + cargados + " procesos pendientes desde memoria secundaria");
            
            // Admitir los nuevos procesos a la cola de listos
            for (int i = 0; i < cargados; i++) {
                admitirProceso();
            }
        }
        
        return cargados;
    }
    
    public int admitirProceso() {
        try {
            if (memoriaPrincipal.colaTrabajosVacia()) {
                registrarLog("No hay procesos en cola de trabajos para admitir");
                return -1;
            }
            
            int numeroBCP = memoriaPrincipal.desencolarTrabajo();
            BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);
            
            if (bcp == null) {
                registrarLog("ERROR: BCP no encontrado al admitir proceso");
                return -1;
            }
            
            bcp.setEstado(EstadoProceso.LISTO);
            memoriaPrincipal.actualizarBCP(numeroBCP, bcp);
            memoriaPrincipal.encolarListo(numeroBCP);
            planificador.onProcesoAgregado(bcp);
            
            registrarLog("Proceso '" + bcp.getNombreProceso() + "' (ID: " + 
                        bcp.getIdProceso() + ") admitido a cola de listos");
            
            return bcp.getIdProceso();
            
        } catch (Exception e) {
            registrarLog("ERROR al admitir proceso: " + e.getMessage());
            return -1;
        }
    }
    
    public int admitirTodosProcesos() {
        int admitidos = 0;
        
        while (!memoriaPrincipal.colaTrabajosVacia()) {
            if (admitirProceso() > 0) {
                admitidos++;
            } else {
                break;
            }
        }
        
        if (admitidos > 0) {
            registrarLog("Total de procesos admitidos: " + admitidos);
        }
        
        return admitidos;
    }
    
    // ========== EJECUCIÓN DE PROCESOS ==========
    
    public boolean iniciarSistema() {
        if (sistemaActivo) {
            registrarLog("El sistema ya está activo");
            return false;
        }
        
        if (memoriaPrincipal.colaListosVacia()) {
            registrarLog("ERROR: No hay procesos en cola de listos para ejecutar");
            return false;
        }
        
        sistemaActivo = true;
        registrarLog("========== SISTEMA OPERATIVO INICIADO ==========");
        registrarLog("Planificador: " + planificador.getNombre());
        
        despacharSiguienteProceso();
        
        return true;
    }
    
    public void detenerSistema() {
        if (!sistemaActivo) {
            return;
        }
        
        detenerEjecucionAutomatica();
        despachador.detener();
        sistemaActivo = false;
        
        registrarLog("========== SISTEMA OPERATIVO DETENIDO ==========");
    }
    
    public boolean despacharSiguienteProceso() {
        try {
            if (memoriaPrincipal.colaListosVacia()) {
                registrarLog("No hay procesos listos para despachar");
                return false;
            }
            
            int numeroBCP = planificador.seleccionarSiguiente(memoriaPrincipal);
            
            if (numeroBCP < 0) {
                registrarLog("ERROR: El planificador no pudo seleccionar un proceso");
                return false;
            }
            
            despachador.despachar(numeroBCP);
            
            BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);
            registrarLog("Proceso '" + bcp.getNombreProceso() + "' despachado para ejecución");
            
            return true;
            
        } catch (Exception e) {
            registrarLog("ERROR al despachar proceso: " + e.getMessage());
            return false;
        }
    }
    
    public boolean ejecutarSiguienteInstruccion() {
        try {
            int numeroBCP = memoriaPrincipal.getBCPEnEjecucion();
            
            if (numeroBCP < 0) {
                registrarLog("No hay proceso en ejecución");
                return false;
            }
            
            BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);
            
            if (bcp == null) {
                registrarLog("ERROR: BCP no encontrado");
                return false;
            }
            
            boolean continua = ejecutor.ejecutarSiguiente();
            bcp = memoriaPrincipal.obtenerBCP(numeroBCP);

            if (!continua || bcp.getEstado() == EstadoProceso.FINALIZADO) {
                finalizarProcesoActual();
                
                // Intentar cargar procesos pendientes si hay
                if (hayProgramasPendientes()) {
                    cargarProcesosPendientes();
                }
                
                // Despachar siguiente si hay procesos listos
                if (!memoriaPrincipal.colaListosVacia()) {
                    despacharSiguienteProceso();
                } else if (hayProgramasPendientes()) {
                    registrarLog("Esperando cargar más procesos desde memoria secundaria...");
                } else {
                    registrarLog("No hay más procesos para ejecutar");
                }
                
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            registrarLog("ERROR en ejecución: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public void iniciarEjecucionAutomatica() {
        if (ejecucionAutomatica) {
            registrarLog("La ejecución automática ya está activa");
            return;
        }
        
        if (!sistemaActivo) {
            registrarLog("ERROR: El sistema no está activo");
            return;
        }
        
        ejecucionAutomatica = true;
        timerEjecucion = new Timer();
        
        timerEjecucion.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!ejecutarSiguienteInstruccion()) {
                    // Verificar si hay más trabajo por hacer
                    if (!hayProgramasPendientes() && memoriaPrincipal.colaListosVacia()) {
                        detenerEjecucionAutomatica();
                    }
                }
            }
        }, 0, 1000);
        
        registrarLog("Ejecución automática iniciada (1 instrucción/segundo)");
    }
    
    public void detenerEjecucionAutomatica() {
        if (!ejecucionAutomatica) {
            return;
        }
        
        if (timerEjecucion != null) {
            timerEjecucion.cancel();
            timerEjecucion = null;
        }
        
        ejecucionAutomatica = false;
        registrarLog("Ejecución automática detenida");
    }
    
    /**
     * Finaliza el proceso actual, genera estadísticas y libera recursos
     */
    private void finalizarProcesoActual() {
        int numeroBCP = memoriaPrincipal.getBCPEnEjecucion();

        if (numeroBCP < 0) {
            return;
        }

        BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);

        if (bcp == null) {
            return;
        }

        String nombreProceso = bcp.getNombreProceso();
        
        // Generar estadísticas
        EstadisticasProceso stats = new EstadisticasProceso(
            bcp.getIdProceso(),
            bcp.getNombreProceso(),
            bcp.getTiempoInicio(),
            bcp.getTiempoCPUUsado(),
            bcp.getRafaga(),
            bcp.getEstado()              
        );        
        
        estadisticas.put(bcp.getIdProceso(), stats);
        planificador.onProcesoFinalizado(bcp);

        // Liberar recursos
        registrarLog("Liberando recursos del proceso '" + nombreProceso + 
                     "' (BCP=" + bcp.getDireccionBase() + ", tamaño=" + bcp.getTamanoProceso() + ")");
        
        memoriaPrincipal.liberarInstrucciones(bcp.getDireccionBase(), bcp.getTamanoProceso());
        memoriaPrincipal.liberarBCP(numeroBCP);

        registrarLog("✓ Proceso '" + nombreProceso + "' (ID: " + bcp.getIdProceso() + ") FINALIZADO");
        registrarLog("  - Instrucciones ejecutadas: " + bcp.getPC());
        registrarLog("  - Tiempo de CPU: " + bcp.getTiempoCPUUsado() + " segundos");
        registrarLog("  - Espacios libres en memoria: " + memoriaPrincipal.getEspacioLibreUsuario());
        
        // Notificar listener
        if (cargaListener != null) {
            cargaListener.onProcesoFinalizado(nombreProceso);
        }
    }
    
    // ========== GESTIÓN DE PLANIFICADOR ==========
    
    public void setPlanificador(IPlanificador nuevoPlanificador) {
        if (nuevoPlanificador == null) {
            registrarLog("ERROR: El planificador no puede ser nulo");
            return;
        }
        
        this.planificador = nuevoPlanificador;
        registrarLog("Planificador cambiado a: " + planificador.getNombre());
    }
    
    public IPlanificador getPlanificador() {
        return planificador;
    }
    
    // ========== CONSULTAS Y ESTADO ==========
    
    public BCP getProcesoEnEjecucion() {
        int numeroBCP = memoriaPrincipal.getBCPEnEjecucion();
        if (numeroBCP < 0) {
            return null;
        }
        return memoriaPrincipal.obtenerBCP(numeroBCP);
    }
    
    public List<BCP> getBCPsActivos() {
        List<BCP> bcps = new ArrayList<>();
        
        for (int i = 0; i < memoriaPrincipal.getMaxProcesos(); i++) {
            BCP bcp = memoriaPrincipal.obtenerBCP(i);
            if (bcp != null) {
                bcps.add(bcp);
            }
        }
        
        return bcps;
    }
    
    public List<BCP> getColaListos() {
        List<BCP> cola = new ArrayList<>();
        int[] indices = memoriaPrincipal.obtenerColaListos();
        
        for (int i : indices) {
            BCP bcp = memoriaPrincipal.obtenerBCP(i);
            if (bcp != null) {
                cola.add(bcp);
            }
        }
        
        return cola;
    }
    
    public List<BCP> getColaTrabajos() {
        List<BCP> cola = new ArrayList<>();
        int[] indices = memoriaPrincipal.obtenerColaTrabajos();
        
        for (int i : indices) {
            BCP bcp = memoriaPrincipal.obtenerBCP(i);
            if (bcp != null) {
                cola.add(bcp);
            }
        }
        
        return cola;
    }
    
    public EstadisticasProceso getEstadisticasProceso(int idProceso) {
        return estadisticas.get(idProceso);
    }
    
    public Map<Integer, EstadisticasProceso> getTodasEstadisticas() {
        return new HashMap<>(estadisticas);
    }
    
    public List<String> getLog() {
        return new ArrayList<>(log);
    }
    
    public List<String> getPantalla() {
        return ejecutor.getPantalla();
    }
    
    public void limpiarPantalla() {
        ejecutor.limpiarPantalla();
    }
    
    public boolean isSistemaActivo() {
        return sistemaActivo;
    }
    
    public boolean isEjecucionAutomatica() {
        return ejecucionAutomatica;
    }
    
    public void setCargaProcesoListener(CargaProcesoListener listener) {
        this.cargaListener = listener;
    }
    
    // ========== REPORTES ==========
    
    public String generarReporteCompleto() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("========================================\n");
        sb.append("    REPORTE DEL SISTEMA OPERATIVO\n");
        sb.append("========================================\n\n");
        
        sb.append("ESTADO GENERAL:\n");
        sb.append("  Sistema activo: ").append(sistemaActivo ? "Sí" : "No").append("\n");
        sb.append("  Ejecución automática: ").append(ejecucionAutomatica ? "Sí" : "No").append("\n");
        sb.append("  Planificador: ").append(planificador.getNombre()).append("\n");
        sb.append("  Programas pendientes: ").append(programasPendientes.size()).append("\n\n");
        
        sb.append(memoriaPrincipal.generarReporte()).append("\n");
        sb.append(ejecutor.generarReporteCPU()).append("\n");
        
        BCP procesoActual = getProcesoEnEjecucion();
        if (procesoActual != null) {
            sb.append("PROCESO EN EJECUCIÓN:\n");
            sb.append("  ").append(procesoActual.toString()).append("\n");
            sb.append("  Progreso: ").append(String.format("%.1f%%", procesoActual.getProgreso())).append("\n\n");
        }
        
        sb.append("COLAS:\n");
        sb.append("  Cola de trabajos: ").append(memoriaPrincipal.obtenerColaTrabajos().length).append(" proceso(s)\n");
        sb.append("  Cola de listos: ").append(memoriaPrincipal.obtenerColaListos().length).append(" proceso(s)\n\n");
        
        if (!estadisticas.isEmpty()) {
            sb.append("PROCESOS FINALIZADOS:\n");
            for (EstadisticasProceso stats : estadisticas.values()) {
                sb.append("  ").append(stats.toString()).append("\n");
            }
        }
        
        sb.append("========================================\n");
        
        return sb.toString();
    }
    
    public String generarReporteEstadisticas() {
        if (estadisticas.isEmpty()) {
            return "No hay estadísticas disponibles\n";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("       ESTADÍSTICAS DE PROCESOS\n");
        sb.append("========================================\n\n");
        
        sb.append(String.format("%-20s | %8s | %8s | %14s | %12s\n",
            "Proceso", "Inicio", "Fin", "Duración", "Instrucciones"));
        sb.append("-".repeat(80)).append("\n");
        
        for (EstadisticasProceso stats : estadisticas.values()) {
            sb.append(stats.toString()).append("\n");
        }
        
        sb.append("========================================\n");
        
        return sb.toString();
    }
    
    // ========== UTILIDADES ==========
    
    private void registrarLog(String mensaje) {
        String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String logMsg = "[" + timestamp + "] " + mensaje;
        log.add(logMsg);
        System.out.println(logMsg);
    }
    
    public void limpiarLog() {
        log.clear();
    }
    
    public void reiniciarSistema() {
        detenerSistema();
        ejecutor.limpiarPantalla();
        planificador.reiniciar();
        estadisticas.clear();
        programasPendientes.clear();
        registrarLog("Sistema reiniciado");
    }
    
    // ========== GETTERS ==========
    
    public MemoriaSecundaria getMemoriaSecundaria() {
        return memoriaSecundaria;
    }
    
    public MemoriaPrincipal getMemoriaPrincipal() {
        return memoriaPrincipal;
    }
    
    public Despachador getDespachador() {
        return despachador;
    }
    
    public EjecutorInstrucciones getEjecutor() {
        return ejecutor;
    }
    
    public CPU getCPU() {
        return ejecutor.getCPU();
    }
}