package so.planificacion;

import so.memoria.MemoriaPrincipalV2;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.EstadoProceso;

/**
 * Implementación del algoritmo Round Robin (RR).
 * 
 * Características:
 * - Apropiativo (preemptive)
 * - Cada proceso recibe un quantum de tiempo de CPU
 * - Cuando se agota el quantum, el proceso vuelve al final de la cola
 * - Justo para todos los procesos
 * - Buen tiempo de respuesta
 * 
 * @author dylan
 */
public class PlanificadorRR implements IPlanificador {
    
    private int quantum;
    private int tiempoEjecutado;
    private int procesoActual;
    
    /**
     * Constructor con quantum por defecto de 3 segundos
     */
    public PlanificadorRR() {
        this(3);
    }
    
    /**
     * Constructor con quantum personalizado
     * @param quantum tiempo en segundos que cada proceso puede ejecutar
     */
    public PlanificadorRR(int quantum) {
        if (quantum < 1) {
            throw new IllegalArgumentException("El quantum debe ser al menos 1");
        }
        this.quantum = quantum;
        this.tiempoEjecutado = 0;
        this.procesoActual = -1;
    }
    
    @Override
    public int seleccionarSiguiente(MemoriaPrincipalV2 memoria) {
        int procesoEnEjecucion = memoria.getBCPEnEjecucion();
        
        // Si hay un proceso en ejecución, verificar su quantum
        if (procesoEnEjecucion >= 0) {
            BCP bcp = memoria.obtenerBCP(procesoEnEjecucion);
            
            if (bcp != null && bcp.getEstado() == EstadoProceso.EJECUCION) {
                tiempoEjecutado++;
                
                // Si no ha agotado el quantum, continuar con él
                if (tiempoEjecutado < quantum) {
                    return -2; // Código especial: continuar con el actual
                }
                
                // Quantum agotado, desalojar proceso
                System.out.println("[RR] Quantum agotado para " + bcp.getNombreProceso() + 
                                 " (ejecutó " + tiempoEjecutado + " segundos)");
                
                bcp.setEstado(EstadoProceso.LISTO);
                memoria.actualizarBCP(procesoEnEjecucion, bcp);
                memoria.encolarListo(procesoEnEjecucion);
                memoria.setBCPEnEjecucion(-1);
                
                tiempoEjecutado = 0;
                procesoActual = -1;
            }
        }
        
        // Seleccionar el siguiente proceso de la cola (FIFO)
        int siguiente = memoria.desencolarListo();
        
        if (siguiente >= 0) {
            BCP bcp = memoria.obtenerBCP(siguiente);
            if (bcp != null) {
                bcp.setQuantumRestante(quantum);
                memoria.actualizarBCP(siguiente, bcp);
                tiempoEjecutado = 0;
                procesoActual = siguiente;
                
                System.out.println("[RR] Seleccionado: " + bcp.getNombreProceso() + 
                                 " (quantum: " + quantum + " segundos)");
            }
        }
        
        return siguiente;
    }
    
    @Override
    public String getNombre() {
        return "Round Robin (quantum=" + quantum + "s)";
    }
    
    @Override
    public void onProcesoAgregado(BCP bcp) {
        bcp.reiniciarQuantum(quantum);
        System.out.println("[RR] Proceso " + bcp.getNombreProceso() + 
                         " agregado con quantum de " + quantum + " segundos");
    }
    
    @Override
    public void onProcesoFinalizado(BCP bcp) {
        if (procesoActual == bcp.getIdProceso()) {
            tiempoEjecutado = 0;
            procesoActual = -1;
        }
    }
    
    @Override
    public void reiniciar() {
        tiempoEjecutado = 0;
        procesoActual = -1;
    }
    
    /**
     * Cambia el quantum dinámicamente
     * @param nuevoQuantum nuevo valor del quantum
     */
    public void setQuantum(int nuevoQuantum) {
        if (nuevoQuantum < 1) {
            throw new IllegalArgumentException("El quantum debe ser al menos 1");
        }
        this.quantum = nuevoQuantum;
        System.out.println("[RR] Quantum cambiado a " + quantum + " segundos");
    }
    
    public int getQuantum() {
        return quantum;
    }
    
    @Override
    public String toString() {
        return getNombre();
    }
}