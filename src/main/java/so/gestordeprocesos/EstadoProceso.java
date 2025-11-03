package so.gestordeprocesos;

/**
 * Estados posibles de un proceso en el sistema operativo.
 * 
 * @author dylan
 */
public enum EstadoProceso {
    /**
     * Proceso recién creado, aún no cargado en memoria principal
     */
    NUEVO,
    
    /**
     * Proceso cargado en memoria y listo para ejecutarse
     */
    LISTO,
    
    /**
     * Proceso actualmente en ejecución en el CPU
     */
    EJECUCION,
    
    /**
     * Proceso que ha terminado su ejecución
     */
    FINALIZADO;
    
    @Override
    public String toString() {
        return switch (this) {
            case NUEVO -> "Nuevo";
            case LISTO -> "Listo";
            case EJECUCION -> "Ejecución";
            case FINALIZADO -> "Finalizado";
        };
    }
}