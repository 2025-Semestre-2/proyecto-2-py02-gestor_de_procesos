package so.planificacion;

import so.memoria.MemoriaPrincipal;
import so.gestordeprocesos.BCP;

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
    public int seleccionarSiguiente(MemoriaPrincipal memoria) {
        // FIFO simplemente desencola el primer elemento de la cola de listos
        return memoria.desencolarListo();
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