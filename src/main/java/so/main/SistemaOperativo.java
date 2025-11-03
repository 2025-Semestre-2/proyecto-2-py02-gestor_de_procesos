package so.main;

import so.memoria.MemoriaPrincipal;
import so.memoria.MemoriaSecundaria;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.Despachador;
import so.gestordeprocesos.EstadoProceso;
import so.instrucciones.EjecutorInstrucciones;
import so.instrucciones.Instruccion;
import so.instrucciones.InstruccionParser;
import so.planificacion.IPlanificador;
import so.planificacion.*;
import so.estadisticas.EstadisticasProceso;

import java.util.*;

/**
 * Clase principal que coordina todos los módulos del sistema operativo.
 * CORREGIDO: Ahora libera correctamente la memoria de procesos finalizados
 * 
 * @author dylan
 */
public class SistemaOperativo {
    
    // ========== COMPONENTES DEL SISTEMA ==========
    private final MemoriaPrincipal memoriaPrincipal;
    private final MemoriaSecundaria memoriaSecundaria;
    private final Despachador despachador;
    private final EjecutorInstrucciones ejecutor;
    private IPlanificador planificador;
    
    // ========== ESTADO DEL SISTEMA ==========
    private boolean sistemaActivo;
    private final Map<Integer, EstadisticasProceso> estadisticas;
    private final List<String> programasPendientes;
    private final Map<Integer, Long> tiemposInicio;
    
    // ========== LISTENERS ==========
    private CargaProcesoListener cargaProcesoListener;
    
    public interface CargaProcesoListener {
        void onProcesoCargado(String nombreProceso);
        void onProcesoFinalizado(String nombreProceso);
    }
    
    /**
     * Constructor del sistema operativo con planificador por defecto (FIFO)
     */
    public SistemaOperativo() {
        this(new PlanificadorHRRN());
    }
    
    /**
     * Constructor con planificador específico
     */
    public SistemaOperativo(IPlanificador planificador) {
        this.memoriaPrincipal = new MemoriaPrincipal();
        this.memoriaSecundaria = new MemoriaSecundaria();
        this.despachador = new Despachador(memoriaPrincipal);
        this.ejecutor = new EjecutorInstrucciones(memoriaPrincipal, despachador);
        this.planificador = planificador;
        
        this.sistemaActivo = false;
        this.estadisticas = new HashMap<>();
        this.programasPendientes = new ArrayList<>();
        this.tiemposInicio = new HashMap<>();
    }
    
    // ========== GESTIÓN DE PROGRAMAS ==========
    
    public boolean cargarProgramasAMemoriaSecundaria(String[] nombres, List<String>[] programas) {
        try {
            memoriaSecundaria.cargarProgramas(nombres, programas);
            programasPendientes.clear();
            programasPendientes.addAll(Arrays.asList(nombres));
            System.out.println("[SO] Se cargaron " + nombres.length + " programas a memoria secundaria");
            return true;
        } catch (Exception e) {
            System.err.println("[SO] Error al cargar programas: " + e.getMessage());
            return false;
        }
    }
    
    public int cargarProcesoAMemoriaPrincipal(String nombrePrograma) {
        try {
            if (memoriaPrincipal.getCantidadBCPsActivos() >= memoriaPrincipal.getMaxProcesos()) {
                System.out.println("[SO] No hay espacio en memoria principal para más procesos");
                return -1;
            }
            
            List<String> lineasPrograma = memoriaSecundaria.leerPrograma(nombrePrograma);
            if (lineasPrograma == null || lineasPrograma.isEmpty()) {
                System.err.println("[SO] Programa no encontrado en memoria secundaria: " + nombrePrograma);
                return -1;
            }
            
            List<Instruccion> instruccionesList = new ArrayList<>();
            for (String linea : lineasPrograma) {
                Instruccion instruccion = InstruccionParser.parse(linea);
                if (instruccion != null) {
                    instruccionesList.add(instruccion);
                }
            }
            
            if (instruccionesList.isEmpty()) {
                System.err.println("[SO] No se pudieron parsear instrucciones válidas");
                return -1;
            }
            
            Instruccion[] instrucciones = instruccionesList.toArray(new Instruccion[0]);
            int direccionBase = memoriaPrincipal.cargarInstrucciones(instrucciones);
            
            if (direccionBase < 0) {
                System.err.println("[SO] No hay espacio suficiente en memoria principal para las instrucciones");
                return -1;
            }
            
            int idProceso = memoriaPrincipal.generarNuevoIDProceso();
            BCP bcp = new BCP(idProceso, nombrePrograma, direccionBase, instrucciones.length);
            
            int numeroBCP = memoriaPrincipal.crearBCP(bcp);
            if (numeroBCP < 0) {
                memoriaPrincipal.liberarInstrucciones(direccionBase, instrucciones.length);
                System.err.println("[SO] No se pudo crear BCP para el proceso");
                return -1;
            }
            
            memoriaPrincipal.encolarTrabajo(numeroBCP);
            tiemposInicio.put(idProceso, System.currentTimeMillis());
            programasPendientes.remove(nombrePrograma);
            
            if (cargaProcesoListener != null) {
                cargaProcesoListener.onProcesoCargado(nombrePrograma);
            }
            
            System.out.println("[SO] Proceso '" + nombrePrograma + "' cargado en memoria principal (ID: " + idProceso + ")");
            return idProceso;
            
        } catch (Exception e) {
            System.err.println("[SO] Error al cargar proceso: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    
    public void admitirTodosProcesos() {
        while (!memoriaPrincipal.colaTrabajosVacia()) {
            int numeroBCP = memoriaPrincipal.desencolarTrabajo();
            if (numeroBCP >= 0) {
                BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);
                if (bcp != null) {
                    bcp.setEstado(EstadoProceso.LISTO);
                    memoriaPrincipal.actualizarBCP(numeroBCP, bcp);
                    memoriaPrincipal.encolarListo(numeroBCP);
                    planificador.onProcesoAgregado(bcp);
                    System.out.println("[SO] Proceso '" + bcp.getNombreProceso() + "' admitido a cola de listos");
                }
            }
        }
    }
    
    // ========== EJECUCIÓN DEL SISTEMA ==========
    
    public boolean iniciarSistema() {
        if (sistemaActivo) {
            System.out.println("[SO] El sistema ya está activo");
            return true;
        }
        sistemaActivo = true;
        System.out.println("[SO] Sistema operativo iniciado");
        System.out.println("[SO] Planificador: " + planificador.getNombre());
        return true;
    }
    
    public void detenerSistema() {
        sistemaActivo = false;
        System.out.println("[SO] Sistema operativo detenido");
    }
    
    /**
     * Ejecuta la siguiente instrucción del proceso actual
     * CORREGIDO: Ahora verifica y libera correctamente procesos finalizados
     */
    public boolean ejecutarSiguienteInstruccion() {
        if (!sistemaActivo) {
            System.out.println("[SO] Sistema no activo");
            return false;
        }

        // PASO 1: Verificar si hay un proceso finalizado que liberar
        verificarYLiberarProcesoFinalizado();

        // PASO 2: Si no hay proceso en ejecución, seleccionar siguiente
        if (memoriaPrincipal.getBCPEnEjecucion() < 0) {
            // Intentar cargar procesos pendientes antes de seleccionar
            cargarProcesosPendientes();
            
            if (!seleccionarSiguienteProceso()) {
                // No hay más trabajo
                return false;
            }
        }

        // PASO 3: Ejecutar instrucción del proceso actual
        boolean continuar = ejecutor.ejecutarSiguiente();

        // PASO 4: Si el proceso terminó, liberarlo inmediatamente
        if (!continuar) {
            verificarYLiberarProcesoFinalizado();
            
            // Intentar cargar procesos pendientes
            cargarProcesosPendientes();
            
            // Intentar seleccionar siguiente proceso
            seleccionarSiguienteProceso();
        }

        return true;
    }

    /**
     * Verifica si el proceso actual está finalizado y lo libera
     */
    private void verificarYLiberarProcesoFinalizado() {
        int numeroBCPActual = memoriaPrincipal.getBCPEnEjecucion();
        
        if (numeroBCPActual < 0) {
            return; // No hay proceso en ejecución
        }
        
        BCP bcpActual = memoriaPrincipal.obtenerBCP(numeroBCPActual);
        
        if (bcpActual == null) {
            memoriaPrincipal.setBCPEnEjecucion(-1);
            return;
        }
        
        // Verificar si está finalizado
        if (bcpActual.getEstado() == EstadoProceso.FINALIZADO) {
            System.out.println("[SO] *** Liberando proceso finalizado: " + bcpActual.getNombreProceso() + " ***");
            finalizarProcesoActual();
        }
    }

    private boolean seleccionarSiguienteProceso() {
        int numeroBCP = planificador.seleccionarSiguiente(memoriaPrincipal);
        
        if (numeroBCP >= 0) {
            despachador.despachar(numeroBCP);
            return true;
        }
        
        return false;
    }
    
    /**
     * Finaliza el proceso actualmente en ejecución y libera sus recursos
     * CORREGIDO: Ahora libera correctamente toda la memoria
     */
    private void finalizarProcesoActual() {
        int numeroBCP = memoriaPrincipal.getBCPEnEjecucion();
        if (numeroBCP < 0) {
            return;
        }

        BCP bcp = memoriaPrincipal.obtenerBCP(numeroBCP);
        if (bcp == null) {
            memoriaPrincipal.setBCPEnEjecucion(-1);
            return;
        }

        // Verificar que realmente esté finalizado
        if (bcp.getEstado() != EstadoProceso.FINALIZADO) {
            System.out.println("[SO] Advertencia: Se intentó liberar un proceso no finalizado: " + bcp.getNombreProceso());
            return;
        }

        String nombreProceso = bcp.getNombreProceso();
        int idProceso = bcp.getIdProceso();
        int direccionBase = bcp.getDireccionBase();
        int tamanoProceso = bcp.getTamanoProceso();

        System.out.println("[SO] ==========================================");
        System.out.println("[SO] Liberando recursos del proceso: " + nombreProceso);
        System.out.println("[SO]   ID: " + idProceso);
        System.out.println("[SO]   Dirección base: " + direccionBase);
        System.out.println("[SO]   Tamaño: " + tamanoProceso + " instrucciones");
        System.out.println("[SO]   Número BCP: " + numeroBCP);

        // 1. Limpiar proceso en ejecución
        memoriaPrincipal.setBCPEnEjecucion(-1);

        // 2. Liberar instrucciones del área de usuario
        memoriaPrincipal.liberarInstrucciones(direccionBase, tamanoProceso);
        System.out.println("[SO]   ✓ Instrucciones liberadas");

        // 3. Registrar estadísticas ANTES de liberar el BCP
        registrarEstadisticas(bcp);

        // 4. Notificar al planificador
        planificador.onProcesoFinalizado(bcp);

        // 5. Liberar BCP (esto debe ser lo último)
        memoriaPrincipal.liberarBCP(numeroBCP);
        System.out.println("[SO]   ✓ BCP liberado");

        // 6. Notificar listener
        if (cargaProcesoListener != null) {
            cargaProcesoListener.onProcesoFinalizado(nombreProceso);
        }

        // 7. Verificar espacio disponible
        int espacioLibre = memoriaPrincipal.getEspacioLibreUsuario();
        int bcpsActivos = memoriaPrincipal.getCantidadBCPsActivos();
        
        System.out.println("[SO]   Espacio libre ahora: " + espacioLibre + " KB");
        System.out.println("[SO]   BCPs activos ahora: " + bcpsActivos + "/" + memoriaPrincipal.getMaxProcesos());
        System.out.println("[SO] ==========================================");
    }
    
    /**
     * Carga procesos pendientes si hay espacio disponible
     * MEJORADO: Ahora es más agresivo en cargar procesos
     */
    private void cargarProcesosPendientes() {
        if (programasPendientes.isEmpty()) {
            return;
        }
        
        int espaciosLibres = memoriaPrincipal.getMaxProcesos() - memoriaPrincipal.getCantidadBCPsActivos();
        
        if (espaciosLibres <= 0) {
            return;
        }
        
        System.out.println("[SO] Hay " + espaciosLibres + " espacios libres. Intentando cargar procesos pendientes...");
        
        int cargados = 0;
        while (espaciosLibres > 0 && !programasPendientes.isEmpty()) {
            String programa = programasPendientes.get(0);
            System.out.println("[SO] Intentando cargar: " + programa);
            
            int id = cargarProcesoAMemoriaPrincipal(programa);
            
            if (id > 0) {
                cargados++;
                espaciosLibres--;
                
                // Admitir el proceso recién cargado inmediatamente
                admitirProcesosRecienCargados();
                
                System.out.println("[SO] ✓ Proceso cargado exitosamente: " + programa);
            } else {
                System.out.println("[SO] ✗ No se pudo cargar: " + programa);
                break;
            }
        }
        
        if (cargados > 0) {
            System.out.println("[SO] Se cargaron " + cargados + " procesos pendientes");
        }
        
        if (!programasPendientes.isEmpty()) {
            System.out.println("[SO] Quedan " + programasPendientes.size() + " procesos en espera");
        }
    }
    
    private void admitirProcesosRecienCargados() {
        for (int i = 0; i < memoriaPrincipal.getMaxProcesos(); i++) {
            BCP bcp = memoriaPrincipal.obtenerBCP(i);
            if (bcp != null && bcp.getEstado() == EstadoProceso.NUEVO) {
                bcp.setEstado(EstadoProceso.LISTO);
                memoriaPrincipal.actualizarBCP(i, bcp);
                memoriaPrincipal.encolarListo(i);
                planificador.onProcesoAgregado(bcp);
                System.out.println("[SO] Proceso '" + bcp.getNombreProceso() + "' admitido a cola de listos");
            }
        }
    }
    
    // ========== ESTADÍSTICAS ==========
    
    private void registrarEstadisticas(BCP bcp) {
        Long inicio = tiemposInicio.get(bcp.getIdProceso());
        if (inicio == null) return;
        
        long duracionSegundos = bcp.getTiempoCPUUsado();
        EstadisticasProceso stats = new EstadisticasProceso(
            bcp.getIdProceso(),
            bcp.getNombreProceso(),
            inicio,
            (int) duracionSegundos,
            bcp.getRafaga(),
            bcp.getEstado()
        );
        
        estadisticas.put(bcp.getIdProceso(), stats);
        tiemposInicio.remove(bcp.getIdProceso());
    }
    
    // ========== GETTERS Y SETTERS ==========
    
    public MemoriaPrincipal getMemoriaPrincipal() {
        return memoriaPrincipal;
    }
    
    public MemoriaSecundaria getMemoriaSecundaria() {
        return memoriaSecundaria;
    }
    
    public EjecutorInstrucciones getEjecutor() {
        return ejecutor;
    }
    
    public boolean isSistemaActivo() {
        return sistemaActivo;
    }
    
    public List<String> getProgramasPendientes() {
        return new ArrayList<>(programasPendientes);
    }
    
    public boolean hayProgramasPendientes() {
        return !programasPendientes.isEmpty();
    }
    
    public BCP getProcesoEnEjecucion() {
        int numeroBCP = memoriaPrincipal.getBCPEnEjecucion();
        if (numeroBCP >= 0) {
            return memoriaPrincipal.obtenerBCP(numeroBCP);
        }
        return null;
    }
    
    public List<BCP> getBCPsActivos() {
        List<BCP> procesos = new ArrayList<>();
        for (int i = 0; i < memoriaPrincipal.getMaxProcesos(); i++) {
            BCP bcp = memoriaPrincipal.obtenerBCP(i);
            if (bcp != null) {
                procesos.add(bcp);
            }
        }
        return procesos;
    }
    
    public int[] getColaListos() {
        return memoriaPrincipal.obtenerColaListos();
    }
    
    public Map<Integer, EstadisticasProceso> getTodasEstadisticas() {
        return new HashMap<>(estadisticas);
    }
    
    public void setPlanificador(IPlanificador planificador) {
        if (planificador != null) {
            this.planificador = planificador;
            this.planificador.reiniciar();
            System.out.println("[SO] Planificador cambiado a: " + planificador.getNombre());
        }
    }
    
    public void setCargaProcesoListener(CargaProcesoListener listener) {
        this.cargaProcesoListener = listener;
    }
    
    public String generarReporteCompleto() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== SISTEMA OPERATIVO - REPORTE COMPLETO ==========\n");
        sb.append("Estado: ").append(sistemaActivo ? "ACTIVO" : "INACTIVO").append("\n");
        sb.append("Planificador: ").append(planificador.getNombre()).append("\n");
        sb.append("\n");
        
        sb.append(memoriaPrincipal.generarReporte()).append("\n");
        
        sb.append("=== PROCESOS ACTIVOS ===\n");
        List<BCP> procesos = getBCPsActivos();
        if (procesos.isEmpty()) {
            sb.append("No hay procesos activos\n");
        } else {
            for (BCP proceso : procesos) {
                sb.append(String.format("- %s (ID: %d, Estado: %s, PC: %d/%d)\n",
                    proceso.getNombreProceso(), proceso.getIdProceso(), 
                    proceso.getEstado(), proceso.getPC(), proceso.getTamanoProceso()));
            }
        }
        
        sb.append("\n=== PROGRAMAS PENDIENTES ===\n");
        if (programasPendientes.isEmpty()) {
            sb.append("No hay programas pendientes\n");
        } else {
            for (String programa : programasPendientes) {
                sb.append("- ").append(programa).append("\n");
            }
        }
        
        sb.append("\n=== COLA DE LISTOS ===\n");
        int[] colaListos = getColaListos();
        if (colaListos.length == 0) {
            sb.append("Cola vacía\n");
        } else {
            for (int numBCP : colaListos) {
                BCP bcp = memoriaPrincipal.obtenerBCP(numBCP);
                if (bcp != null) {
                    sb.append("- ").append(bcp.getNombreProceso()).append("\n");
                }
            }
        }
        
        sb.append("========================================================\n");
        return sb.toString();
    }
}