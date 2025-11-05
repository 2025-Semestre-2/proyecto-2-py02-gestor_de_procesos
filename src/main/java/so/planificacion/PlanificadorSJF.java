package so.planificacion;

import so.memoria.MemoriaPrincipalV2;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.EstadoProceso;
import java.util.*;

/**
 * Implementación del algoritmo Shortest Job First (SJF).
 * 
 * Características:
 * - No apropiativo (non-preemptive)
 * - Selecciona el proceso con la ráfaga de CPU más corta
 * - Minimiza el tiempo de espera promedio
 * - Puede causar inanición en procesos largos
 * 
 * @author dylan
 */
public class PlanificadorSJF implements IPlanificador {
    
    @Override
    public int seleccionarSiguiente(MemoriaPrincipalV2 memoria) {
        int[] colaListos = memoria.obtenerColaListos();
        
        if (colaListos.length == 0) {
            return -1;
        }
        
        // Buscar el proceso con la ráfaga más corta
        int mejorIndice = -1;
        int mejorNumeroBCP = -1;
        int menorRafaga = Integer.MAX_VALUE;
        
        for (int i = 0; i < colaListos.length; i++) {
            int numeroBCP = colaListos[i];
            BCP bcp = memoria.obtenerBCP(numeroBCP);
            
            if (bcp != null && bcp.getEstado() == EstadoProceso.LISTO) {
                int rafagaRestante = bcp.getTamanoProceso() - bcp.getPC();
                
                if (rafagaRestante < menorRafaga) {
                    menorRafaga = rafagaRestante;
                    mejorIndice = i;
                    mejorNumeroBCP = numeroBCP;
                }
            }
        }
        
        if (mejorNumeroBCP >= 0) {
            // Remover de la cola manualmente
            removerDeCola(memoria, mejorIndice);
            return mejorNumeroBCP;
        }
        
        return -1;
    }
    
    /**
     * Remueve un elemento de la cola de listos en un índice específico
     */
    private void removerDeCola(MemoriaPrincipalV2 memoria, int indice) {
        int[] cola = memoria.obtenerColaListos();
        
        // Desencolar todos
        List<Integer> temp = new ArrayList<>();
        while (!memoria.colaListosVacia()) {
            temp.add(memoria.desencolarListo());
        }
        
        // Re-encolar sin el elemento removido
        for (int i = 0; i < temp.size(); i++) {
            if (i != indice) {
                memoria.encolarListo(temp.get(i));
            }
        }
    }
    
    @Override
    public String getNombre() {
        return "SJF (Shortest Job First)";
    }
    
    @Override
    public void onProcesoAgregado(BCP bcp) {
        // SJF no necesita hacer nada especial
    }
    
    @Override
    public void onProcesoFinalizado(BCP bcp) {
        // SJF no necesita hacer nada especial
    }
    
    @Override
    public void reiniciar() {
        // SJF no mantiene estado interno
    }
    
    @Override
    public String toString() {
        return getNombre();
    }
}