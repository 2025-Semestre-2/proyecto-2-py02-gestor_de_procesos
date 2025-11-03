package so.estadisticas;

import so.gestordeprocesos.EstadoProceso;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Clase para almacenar y presentar estadísticas de ejecución de un proceso.
 * 
 * Información requerida según la rúbrica:
 * - Nombre del proceso
 * - Hora:minuto:segundo de inicio
 * - Hora:minuto:segundo de finalización
 * - Duración en segundos
 * 
 * @author dylan
 */
public class EstadisticasProceso {
    
    private final String nombreProceso;
    private final int idProceso;
    private final LocalDateTime horaInicio;
    private LocalDateTime horaFin;
    private final int duracionSegundos;
    private final int rafaga;
    private final EstadoProceso estadoFinal;
    
    private static final DateTimeFormatter FORMATO_HORA = 
            DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Constructor para crear estadísticas de un proceso
     * 
     * @param idProceso ID del proceso
     * @param nombre nombre del proceso
     * @param timestampInicio timestamp de inicio en milisegundos
     * @param duracion duración en segundos
     * @param rafaga ráfaga del proceso (número de instrucciones)
     * @param estado estado final del proceso
     */
    public EstadisticasProceso(int idProceso, String nombre, long timestampInicio, 
                               int duracion, int rafaga, EstadoProceso estado) {
        this.idProceso = idProceso;
        this.nombreProceso = nombre;
        this.horaInicio = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestampInicio), 
            ZoneId.systemDefault()
        );
        this.duracionSegundos = duracion;
        this.rafaga = rafaga;
        this.estadoFinal = estado;
        
        // Calcular hora de fin si el proceso terminó
        if (estado == EstadoProceso.FINALIZADO) {
            this.horaFin = horaInicio.plusSeconds(duracion);
        }
    }
    
    /**
     * Obtiene la hora de inicio formateada (HH:mm:ss)
     */
    public String getHoraInicioFormateada() {
        return horaInicio.format(FORMATO_HORA);
    }
    
    /**
     * Obtiene la hora de fin formateada (HH:mm:ss)
     */
    public String getHoraFinFormateada() {
        if (horaFin == null) {
            return "N/A";
        }
        return horaFin.format(FORMATO_HORA);
    }
    
    /**
     * Genera una representación en tabla de las estadísticas
     */
    @Override
    public String toString() {
        return String.format("%-20s | %8s | %8s | %10d seg | %8d instr",
            nombreProceso, 
            getHoraInicioFormateada(), 
            getHoraFinFormateada(), 
            duracionSegundos, 
            rafaga);
    }
    
    /**
     * Genera un reporte detallado de las estadísticas
     */
    public String generarReporteDetallado() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append(String.format("Proceso: %s (ID: %d)\n", nombreProceso, idProceso));
        sb.append(String.format("Inicio: %s\n", getHoraInicioFormateada()));
        sb.append(String.format("Fin: %s\n", getHoraFinFormateada()));
        sb.append(String.format("Duración: %d segundos\n", duracionSegundos));
        sb.append(String.format("Ráfaga: %d instrucciones\n", rafaga));
        sb.append(String.format("Estado final: %s\n", estadoFinal));
        
        if (rafaga > 0) {
            double tiempoPorInstruccion = (double) duracionSegundos / rafaga;
            sb.append(String.format("Tiempo por instrucción: %.2f seg\n", tiempoPorInstruccion));
        }
        
        sb.append("========================================\n");
        return sb.toString();
    }
    
    // ========== GETTERS ==========
    
    public String getNombreProceso() {
        return nombreProceso;
    }
    
    public int getIdProceso() {
        return idProceso;
    }
    
    public LocalDateTime getHoraInicio() {
        return horaInicio;
    }
    
    public LocalDateTime getHoraFin() {
        return horaFin;
    }
    
    public int getDuracionSegundos() {
        return duracionSegundos;
    }
    
    public int getRafaga() {
        return rafaga;
    }
    
    public EstadoProceso getEstadoFinal() {
        return estadoFinal;
    }
}