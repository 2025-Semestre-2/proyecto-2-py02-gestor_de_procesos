package so.memoria.estrategias;

import so.instrucciones.Instruccion;
import so.gestordeprocesos.BCP;

/**
 * Interfaz para implementar diferentes estrategias de particionamiento de memoria.
 * Permite cambiar dinámicamente la estrategia de gestión de memoria del usuario.
 * 
 * @author dylan
 */
public interface IEstrategiaParticionamiento {
    
    /**
     * Inicializa la estrategia de particionamiento
     * 
     * @param memoriaUsuario referencia al área de usuario
     * @param tamanoUsuario tamaño total del área de usuario
     * @param inicioUsuario dirección de inicio del área de usuario
     */
    void inicializar(Object[] memoriaUsuario, int tamanoUsuario, int inicioUsuario);
    
    /**
     * Carga instrucciones en memoria usando la estrategia de particionamiento
     * 
     * @param instrucciones array de instrucciones a cargar
     * @return InfoAsignacion con datos de la asignación o null si no hay espacio
     */
    InfoAsignacion cargarInstrucciones(Instruccion[] instrucciones);
    
    /**
     * Libera el espacio ocupado por un proceso
     * 
     * @param bcp proceso a liberar
     */
    void liberarEspacio(BCP bcp);
    
    /**
     * Asocia la asignación de memoria al BCP
     * 
     * @param bcp proceso al que se asigna
     * @param info información de la asignación
     */
    void asociarAsignacionAProceso(BCP bcp, InfoAsignacion info);
    
    /**
     * Obtiene el espacio libre total
     * 
     * @return espacio libre en KB
     */
    int getEspacioLibreTotal();
    
    /**
     * Obtiene la fragmentación interna total
     * 
     * @return fragmentación interna en KB
     */
    int getFragmentacionInternaTotal();
    
    /**
     * Genera un reporte detallado del estado de la memoria
     * 
     * @return string con información del estado
     */
    String generarReporte();
    
    /**
     * Obtiene el nombre de la estrategia
     * 
     * @return nombre descriptivo
     */
    String getNombre();
    
    /**
     * Reinicia la estrategia a su estado inicial
     */
    void reiniciar();
    
    /**
     * Clase interna para encapsular información de asignación
     */
    class InfoAsignacion {
        public int direccionBase;
        public int tamanoAsignado;
        public int fragmentacionInterna;
        
        // Para particionamiento fijo
        public Integer indiceParticion;
        
        // Para particionamiento dinámico (Buddy System)
        public Integer indiceBloqueMemoria;
        public Integer nivelBuddy;
        public Integer direccionBloque;
        
        // Para segmentación (futuro)
        public Integer indiceTablaSegmentos;
        public Integer numeroSegmentos;
        
        public InfoAsignacion(int direccionBase, int tamanoAsignado, int fragmentacionInterna) {
            this.direccionBase = direccionBase;
            this.tamanoAsignado = tamanoAsignado;
            this.fragmentacionInterna = fragmentacionInterna;
        }
        
        @Override
        public String toString() {
            return String.format("InfoAsignacion[dir=%d, tam=%d, frag=%d]", 
                direccionBase, tamanoAsignado, fragmentacionInterna);
        }
    }
}