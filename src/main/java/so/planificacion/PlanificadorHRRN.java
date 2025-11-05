package so.planificacion;

import so.memoria.MemoriaPrincipalV2;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.EstadoProceso;
import java.util.*;

/**
 * Implementación del algoritmo Highest Response Ratio Next (HRRN).
 * 
 * Características:
 * - No apropiativo (non-preemptive)
 * - Selecciona el proceso con la mayor relación de respuesta
 * - Ratio = (Tiempo de espera + Tiempo de servicio) / Tiempo de servicio
 * - Previene la inanición que puede ocurrir en SJF
 * - Favorece procesos cortos pero también considera el tiempo de espera
 * 
 * Fórmula:
 * Response Ratio = (W + S) / S
 * Donde:
 * - W = Tiempo de espera (tiempo en cola de listos)
 * - S = Tiempo de servicio (ráfaga estimada)
 * 
 * @author dylan
 */
public class PlanificadorHRRN implements IPlanificador {
    
    // Mapa para trackear el tiempo de llegada de cada proceso a la cola
    private final Map<Integer, Long> tiemposLlegada;
    
    public PlanificadorHRRN() {
        this.tiemposLlegada = new HashMap<>();
    }
    
    @Override
    public int seleccionarSiguiente(MemoriaPrincipalV2 memoria) {
        int[] colaListos = memoria.obtenerColaListos();
        
        if (colaListos.length == 0) {
            return -1;
        }
        
        // Calcular el Response Ratio para cada proceso
        int mejorNumeroBCP = -1;
        double mayorRatio = -1;
        int mejorIndice = -1;
        long tiempoActual = System.currentTimeMillis();
        
        System.out.println("[HRRN] Calculando Response Ratios:");
        
        for (int i = 0; i < colaListos.length; i++) {
            int numeroBCP = colaListos[i];
            BCP bcp = memoria.obtenerBCP(numeroBCP);
            
            if (bcp != null && bcp.getEstado() == EstadoProceso.LISTO) {
                // Tiempo de servicio (ráfaga restante)
                int tiempoServicio = bcp.getTamanoProceso() - bcp.getPC();
                
                if (tiempoServicio <= 0) {
                    tiempoServicio = 1; // Evitar división por cero
                }
                
                // Tiempo de espera (desde que llegó a la cola hasta ahora)
                long tiempoLlegada = tiemposLlegada.getOrDefault(bcp.getIdProceso(), tiempoActual);
                long tiempoEspera = (tiempoActual - tiempoLlegada) / 1000; // Convertir a segundos
                
                // Agregar el tiempo de espera acumulado del BCP
                tiempoEspera += bcp.getTiempoEspera();
                
                // Calcular Response Ratio
                double responseRatio = (double)(tiempoEspera + tiempoServicio) / tiempoServicio;
                
                System.out.println(String.format("[HRRN]   %s: W=%d, S=%d, RR=%.3f", 
                    bcp.getNombreProceso(), tiempoEspera, tiempoServicio, responseRatio));
                
                // Seleccionar el proceso con mayor ratio
                if (responseRatio > mayorRatio) {
                    mayorRatio = responseRatio;
                    mejorNumeroBCP = numeroBCP;
                    mejorIndice = i;
                }
            }
        }
        
        if (mejorNumeroBCP >= 0) {
            BCP bcp = memoria.obtenerBCP(mejorNumeroBCP);
            System.out.println(String.format("[HRRN] Seleccionado: %s (RR=%.3f)", 
                bcp.getNombreProceso(), mayorRatio));
            
            // Remover de la cola y limpiar tiempo de llegada
            removerDeCola(memoria, mejorIndice);
            tiemposLlegada.remove(bcp.getIdProceso());
            
            return mejorNumeroBCP;
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
        return "HRRN (Highest Response Ratio Next)";
    }
    
    @Override
    public void onProcesoAgregado(BCP bcp) {
        // Registrar el tiempo de llegada del proceso
        tiemposLlegada.put(bcp.getIdProceso(), System.currentTimeMillis());
        
        System.out.println("[HRRN] Proceso agregado: " + bcp.getNombreProceso() + 
                         " (ráfaga: " + bcp.getTamanoProceso() + ")");
    }
    
    @Override
    public void onProcesoFinalizado(BCP bcp) {
        // Limpiar tiempo de llegada
        tiemposLlegada.remove(bcp.getIdProceso());
    }
    
    @Override
    public void reiniciar() {
        tiemposLlegada.clear();
    }
    
    @Override
    public String toString() {
        return getNombre();
    }
}