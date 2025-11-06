package so.planificacion;

import so.memoria.MemoriaPrincipalV2;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.EstadoProceso;
import java.util.*;

/**
 * Implementación del algoritmo Shortest Remaining Time (SRT).
 * 
 * Características:
 * - Apropiativo (preemptive) - versión apropiativa de SJF
 * - Selecciona el proceso con el menor tiempo restante
 * - Permite interrumpir el proceso actual si llega uno con menos tiempo
 * - Minimiza el tiempo de espera pero puede causar overhead por cambios de contexto
 * 
 * @author dylan
 */
public class PlanificadorSRT implements IPlanificador {
    
    private int procesoAnterior = -1;
    
    @Override
    public int seleccionarSiguiente(MemoriaPrincipalV2 memoria) {
        int[] colaListos = memoria.obtenerColaListos();
        int procesoActual = memoria.getBCPEnEjecucion();
        
        // Si no hay procesos listos
        if (colaListos.length == 0 && procesoActual < 0) {
            return -1;
        }
        
        // Buscar el proceso con el menor tiempo restante
        int mejorNumeroBCP = -1;
        int menorTiempoRestante = Integer.MAX_VALUE;
        int mejorIndice = -1;
        
        // Verificar el proceso actual (si está en ejecución)
        if (procesoActual >= 0) {
            BCP bcpActual = memoria.obtenerBCP(procesoActual);
            if (bcpActual != null && bcpActual.getEstado() == EstadoProceso.EJECUCION) {
                menorTiempoRestante = bcpActual.getTamanoProceso() - bcpActual.getPC();
                mejorNumeroBCP = procesoActual;
            }
        }
        
        // Comparar con procesos en cola de listos
        for (int i = 0; i < colaListos.length; i++) {
            int numeroBCP = colaListos[i];
            BCP bcp = memoria.obtenerBCP(numeroBCP);
            
            if (bcp != null && bcp.getEstado() == EstadoProceso.LISTO) {
                int tiempoRestante = bcp.getTamanoProceso() - bcp.getPC();
                
                if (tiempoRestante < menorTiempoRestante) {
                    menorTiempoRestante = tiempoRestante;
                    mejorNumeroBCP = numeroBCP;
                    mejorIndice = i;
                }
            }
        }
        
        // Si encontramos un proceso mejor que el actual, hacer cambio de contexto
        if (mejorNumeroBCP >= 0 && mejorNumeroBCP != procesoActual) {
            // Si hay un proceso actual, devolverlo a la cola
            if (procesoActual >= 0 && mejorNumeroBCP != procesoActual) {
                BCP bcpActual = memoria.obtenerBCP(procesoActual);
                if (bcpActual != null && bcpActual.getEstado() == EstadoProceso.EJECUCION) {
                    bcpActual.setEstado(EstadoProceso.LISTO);
                    memoria.actualizarBCP(procesoActual, bcpActual);
                    memoria.encolarListo(procesoActual);
                    System.out.println("[SRT] Proceso " + bcpActual.getNombreProceso() + 
                                     " desalojado (tiempo restante: " + 
                                     (bcpActual.getTamanoProceso() - bcpActual.getPC()) + ")");
                }
                memoria.setBCPEnEjecucion(-1);
            }
            
            // Remover el mejor proceso de la cola si no es el actual
            if (mejorIndice >= 0) {
                removerDeCola(memoria, mejorIndice);
            }
            
            procesoAnterior = mejorNumeroBCP;
            return mejorNumeroBCP;
        }
        
        // Si el actual sigue siendo el mejor, continuar con él
        if (procesoActual >= 0) {
            return -2; // Código especial: continuar con el actual
        }
        
        return -1;
    }
    
    /**
     * Remueve un elemento de la cola de listos en un índice específico
     */
    private void removerDeCola(MemoriaPrincipalV2 memoria, int indice) {
        int[] cola = memoria.obtenerColaListos();
        
        List<Integer> temp = new ArrayList<>();
        while (!memoria.colaListosVacia()) {
            temp.add(memoria.desencolarListo());
        }
        
        for (int i = 0; i < temp.size(); i++) {
            if (i != indice) {
                memoria.encolarListo(temp.get(i));
            }
        }
    }
    
    @Override
    public String getNombre() {
        return "SRT (Shortest Remaining Time)";
    }
    
    @Override
    public void onProcesoAgregado(BCP bcp) {
        System.out.println("[SRT] Nuevo proceso agregado: " + bcp.getNombreProceso() + 
                         " (ráfaga: " + bcp.getTamanoProceso() + ")");
    }
    
    @Override
    public void onProcesoFinalizado(BCP bcp) {
        if (procesoAnterior == bcp.getIdProceso()) {
            procesoAnterior = -1;
        }
    }
    
    @Override
    public void reiniciar() {
        procesoAnterior = -1;
    }
    
    @Override
    public String toString() {
        return getNombre();
    }
}