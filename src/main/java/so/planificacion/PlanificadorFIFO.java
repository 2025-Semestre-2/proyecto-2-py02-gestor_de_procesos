package so.planificacion;

import so.memoria.MemoriaPrincipalV2;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.EstadoProceso;

/**
 * Implementación del algoritmo de planificación FIFO (First In First Out).
 * También conocido como FCFS (First Come First Served).
 * 
 * Características:
 * - No es apropiativo (non-preemptive)
 * - El primer proceso que llega es el primero en ejecutarse
 * - Simple de implementar
 * - Puede causar el efecto "convoy" (procesos cortos esperan a largos)
 * 
 * @author dylan
 */
public class PlanificadorFIFO implements IPlanificador {
    
    @Override
    public int seleccionarSiguiente(MemoriaPrincipalV2 memoria) {
        int[] colaListos = memoria.obtenerColaListos();

        if (colaListos.length == 0) {
            return -1;
        }

        // Para FIFO, simplemente el primero de la cola que haya llegado
        for (int numeroBCP : colaListos) {
            BCP bcp = memoria.obtenerBCP(numeroBCP);
            if (bcp != null && bcp.getEstado() == EstadoProceso.LISTO) {
                return memoria.desencolarListo();
            }
        }

        return -1;
    }
    
    @Override
    public String getNombre() {
        return "FIFO (First In First Out)";
    }
    
    @Override
    public void onProcesoAgregado(BCP bcp) {
        // FIFO no necesita hacer nada especial cuando se agrega un proceso
        // La cola de listos ya maneja el orden de llegada
    }
    
    @Override
    public void onProcesoFinalizado(BCP bcp) {
        // FIFO no necesita hacer nada especial cuando un proceso termina
    }
    
    @Override
    public void reiniciar() {
        // FIFO no mantiene estado interno, no necesita reiniciarse
    }
    
    @Override
    public String toString() {
        return getNombre();
    }
}