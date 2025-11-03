package so.planificacion;

import so.memoria.MemoriaPrincipal;
import so.gestordeprocesos.BCP;

/**
 * Interface para implementar diferentes algoritmos de planificación de CPU.
 * Permite cambiar dinámicamente el algoritmo de planificación utilizado.
 * 
 * @author dylan
 */
public interface IPlanificador {
    
    /**
     * Selecciona el siguiente proceso a ejecutar de la cola de listos
     * 
     * @param memoria referencia a la memoria principal
     * @return número de BCP (0-4) seleccionado o -1 si no hay procesos
     */
    int seleccionarSiguiente(MemoriaPrincipal memoria);
    
    /**
     * Obtiene el nombre del algoritmo de planificación
     * 
     * @return nombre del planificador
     */
    String getNombre();
    
    /**
     * Se invoca cuando un nuevo proceso es agregado a la cola de listos
     * Algunos algoritmos necesitan actualizar estructuras internas
     * 
     * @param bcp proceso agregado
     */
    void onProcesoAgregado(BCP bcp);
    
    /**
     * Se invoca cuando un proceso finaliza su ejecución
     * Algunos algoritmos necesitan actualizar estructuras internas
     * 
     * @param bcp proceso finalizado
     */
    void onProcesoFinalizado(BCP bcp);
    
    /**
     * Reinicia el estado interno del planificador
     * Útil cuando se cambia de algoritmo o se reinicia el sistema
     */
    void reiniciar();
}