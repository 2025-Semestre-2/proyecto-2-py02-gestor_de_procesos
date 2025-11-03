package so.gestordeprocesos;

import so.memoria.MemoriaPrincipal;

/**
 * Despachador (Dispatcher) del sistema operativo.
 * 
 * Responsabilidades:
 * - Realizar el cambio de contexto cuando se selecciona un nuevo proceso
 * - Cargar el estado del proceso seleccionado en el CPU
 * - Actualizar el estado del proceso a EJECUCION
 * - Detener la ejecución del proceso actual cuando finaliza o es interrumpido
 * 
 * @author dylan
 */
public class Despachador {
    
    private final MemoriaPrincipal memoria;
    
    /**
     * Constructor del despachador
     * 
     * @param memoria referencia a la memoria principal
     */
    public Despachador(MemoriaPrincipal memoria) {
        if (memoria == null) {
            throw new IllegalArgumentException("La memoria no puede ser nula");
        }
        this.memoria = memoria;
    }
    
    /**
     * Despacha un proceso para ejecución
     * Cambia su estado a EJECUCION y lo marca como proceso en ejecución
     * 
     * @param numeroBCP número del BCP a despachar (0-4)
     * @throws IllegalStateException si el BCP no existe o está en estado inválido
     */
    public void despachar(int numeroBCP) {
        if (numeroBCP < 0 || numeroBCP >= memoria.getMaxProcesos()) {
            throw new IllegalArgumentException("Número de BCP inválido: " + numeroBCP);
        }
        
        // Cargar el BCP desde memoria
        BCP bcp = memoria.obtenerBCP(numeroBCP);
        
        if (bcp == null) {
            throw new IllegalStateException("No existe un BCP en la posición " + numeroBCP);
        }
        
        // Verificar que el proceso esté en estado válido para ejecutarse
        if (bcp.getEstado() == EstadoProceso.FINALIZADO) {
            throw new IllegalStateException(
                "No se puede despachar un proceso finalizado: " + bcp.getNombreProceso()
            );
        }
        
        // Cambiar estado a EJECUCION
        bcp.setEstado(EstadoProceso.EJECUCION);
        
        // Actualizar BCP en memoria
        memoria.actualizarBCP(numeroBCP, bcp);
        
        // Marcar como proceso en ejecución
        memoria.setBCPEnEjecucion(numeroBCP);
        
        System.out.println("[DESPACHADOR] Proceso " + bcp.getNombreProceso() + 
                          " (ID: " + bcp.getIdProceso() + ") despachado para ejecución");
    }
    
    /**
     * Detiene el proceso actualmente en ejecución
     * No cambia su estado, solo lo desmarca como proceso en ejecución
     */
    public void detener() {
        int numeroBCP = memoria.getBCPEnEjecucion();
        
        if (numeroBCP >= 0) {
            BCP bcp = memoria.obtenerBCP(numeroBCP);
            
            if (bcp != null) {
                System.out.println("[DESPACHADOR] Proceso " + bcp.getNombreProceso() + 
                                  " detenido");
            }
            
            memoria.setBCPEnEjecucion(-1);
        }
    }
    
    /**
     * Pausa el proceso actual y lo devuelve a la cola de listos
     * Usado en algoritmos apropiativos como Round Robin
     * 
     * @return número del BCP pausado o -1 si no había proceso en ejecución
     */
    public int pausar() {
        int numeroBCP = memoria.getBCPEnEjecucion();
        
        if (numeroBCP < 0) {
            return -1;
        }
        
        BCP bcp = memoria.obtenerBCP(numeroBCP);
        
        if (bcp != null && bcp.getEstado() == EstadoProceso.EJECUCION) {
            // Cambiar estado a LISTO
            bcp.setEstado(EstadoProceso.LISTO);
            memoria.actualizarBCP(numeroBCP, bcp);
            
            // Devolver a la cola de listos
            memoria.encolarListo(numeroBCP);
            
            System.out.println("[DESPACHADOR] Proceso " + bcp.getNombreProceso() + 
                              " pausado y devuelto a cola de listos");
        }
        
        memoria.setBCPEnEjecucion(-1);
        return numeroBCP;
    }
    
    /**
     * Obtiene información sobre el proceso actualmente en ejecución
     * 
     * @return String con información del proceso o mensaje indicando que no hay proceso
     */
    public String getProcesoEnEjecucion() {
        int numeroBCP = memoria.getBCPEnEjecucion();
        
        if (numeroBCP < 0) {
            return "Ningún proceso en ejecución";
        }
        
        BCP bcp = memoria.obtenerBCP(numeroBCP);
        
        if (bcp == null) {
            return "Error: BCP no encontrado";
        }
        
        return String.format("Proceso en ejecución: %s (ID: %d, Estado: %s, PC: %d/%d)",
                           bcp.getNombreProceso(),
                           bcp.getIdProceso(),
                           bcp.getEstado(),
                           bcp.getPC(),
                           bcp.getTamanoProceso());
    }
}