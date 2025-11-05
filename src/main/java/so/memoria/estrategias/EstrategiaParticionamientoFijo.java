package so.memoria.estrategias;

import so.instrucciones.Instruccion;
import so.gestordeprocesos.BCP;

/**
 * Implementación de Particionamiento Fijo.
 * Gestiona la memoria con particiones de tamaño fijo (iguales o desiguales).
 * 
 * @author dylan
 */
public class EstrategiaParticionamientoFijo implements IEstrategiaParticionamiento {
    
    private Object[] memoriaUsuario;
    private int tamanoUsuario;
    private int inicioUsuario;
    
    // Tabla de particiones: [inicio, tamano, estado(0=libre,1=ocupada), idProceso, numeroBCP]
    private static final int CAMPOS_PARTICION = 5;
    private static final int MAX_PARTICIONES = 58;
    private Particion[] tablaParticiones;
    private int numParticiones;
    
    private TipoParticionamiento tipo;
    private int tamanoParticionIgual;
    
    public enum TipoParticionamiento {
        IGUAL, DESIGUAL
    }
    
    /**
     * Clase interna para representar una partición
     */
    private static class Particion {
        int inicio;
        int tamano;
        boolean ocupada;
        int idProceso;
        int numeroBCP;
        
        Particion(int inicio, int tamano) {
            this.inicio = inicio;
            this.tamano = tamano;
            this.ocupada = false;
            this.idProceso = -1;
            this.numeroBCP = -1;
        }
        
        void ocupar(int idProceso, int numeroBCP) {
            this.ocupada = true;
            this.idProceso = idProceso;
            this.numeroBCP = numeroBCP;
        }
        
        void liberar() {
            this.ocupada = false;
            this.idProceso = -1;
            this.numeroBCP = -1;
        }
    }
    
    /**
     * Constructor para particionamiento IGUAL
     */
    public EstrategiaParticionamientoFijo(int tamanoParticion) {
        this.tipo = TipoParticionamiento.IGUAL;
        this.tamanoParticionIgual = tamanoParticion;
        this.tablaParticiones = new Particion[MAX_PARTICIONES];
    }
    
    /**
     * Constructor para particionamiento DESIGUAL
     */
    public EstrategiaParticionamientoFijo(TipoParticionamiento tipo) {
        if (tipo != TipoParticionamiento.DESIGUAL) {
            throw new IllegalArgumentException("Use el otro constructor para particionamiento igual");
        }
        this.tipo = tipo;
        this.tablaParticiones = new Particion[MAX_PARTICIONES];
    }
    
    @Override
    public void inicializar(Object[] memoriaUsuario, int tamanoUsuario, int inicioUsuario) {
        this.memoriaUsuario = memoriaUsuario;
        this.tamanoUsuario = tamanoUsuario;
        this.inicioUsuario = inicioUsuario;
        
        if (tipo == TipoParticionamiento.IGUAL) {
            configurarParticionamientoIgual();
        } else {
            configurarParticionamientoDesigual();
        }
    }
    
    private void configurarParticionamientoIgual() {
        if (tamanoParticionIgual <= 0 || tamanoParticionIgual > tamanoUsuario) {
            throw new IllegalArgumentException("Tamaño de partición inválido: " + tamanoParticionIgual);
        }
        
        numParticiones = Math.min(tamanoUsuario / tamanoParticionIgual, MAX_PARTICIONES);
        int direccionActual = inicioUsuario;
        
        for (int i = 0; i < numParticiones; i++) {
            tablaParticiones[i] = new Particion(direccionActual, tamanoParticionIgual);
            direccionActual += tamanoParticionIgual;
        }
        
        System.out.println("[PARTICIONAMIENTO FIJO IGUAL] Configurado:");
        System.out.println("  - Tamaño de partición: " + tamanoParticionIgual + " KB");
        System.out.println("  - Número de particiones: " + numParticiones);
        System.out.println("  - Espacio total usado: " + (numParticiones * tamanoParticionIgual) + " KB");
    }
    
    private void configurarParticionamientoDesigual() {
        int direccionActual = inicioUsuario;
        numParticiones = 0;
        int tamanoActual = 2; // Comienza en 2 KB
        int espacioRestante = tamanoUsuario;
        
        while (espacioRestante >= tamanoActual && numParticiones < MAX_PARTICIONES) {
            tablaParticiones[numParticiones] = new Particion(direccionActual, tamanoActual);
            
            direccionActual += tamanoActual;
            espacioRestante -= tamanoActual;
            numParticiones++;
            tamanoActual += 2; // Incrementar de 2 en 2
        }
        
        System.out.println("[PARTICIONAMIENTO FIJO DESIGUAL] Configurado:");
        System.out.println("  - Número de particiones: " + numParticiones);
        System.out.println("  - Tamaños: 2, 4, 6, 8, ..., " + (2 + (numParticiones-1)*2) + " KB");
        System.out.println("  - Espacio no utilizado: " + espacioRestante + " KB");
    }
    
    @Override
    public InfoAsignacion cargarInstrucciones(Instruccion[] instrucciones) {
        int tamanoRequerido = instrucciones.length;
        int indiceParticion = buscarParticionLibre(tamanoRequerido);
        
        if (indiceParticion < 0) {
            System.out.println("[PARTICIONAMIENTO FIJO] No hay partición disponible para " + 
                             tamanoRequerido + " instrucciones");
            return null;
        }
        
        Particion particion = tablaParticiones[indiceParticion];
        
        // Cargar instrucciones
        for (int i = 0; i < instrucciones.length; i++) {
            memoriaUsuario[particion.inicio + i] = instrucciones[i];
        }
        
        int fragmentacionInterna = particion.tamano - tamanoRequerido;
        
        System.out.println("[PARTICIONAMIENTO FIJO] Instrucciones cargadas en partición " + indiceParticion);
        System.out.println("  - Dirección base: " + particion.inicio);
        System.out.println("  - Tamaño partición: " + particion.tamano + " KB");
        System.out.println("  - Tamaño proceso: " + tamanoRequerido + " KB");
        System.out.println("  - Fragmentación interna: " + fragmentacionInterna + " KB");
        
        InfoAsignacion info = new InfoAsignacion(particion.inicio, particion.tamano, fragmentacionInterna);
        info.indiceParticion = indiceParticion;
        
        return info;
    }
    
    private int buscarParticionLibre(int tamanoRequerido) {
        int mejorParticion = -1;
        int menorDesperdicio = Integer.MAX_VALUE;
        
        // Best-fit: buscar la partición más pequeña que quepa
        for (int i = 0; i < numParticiones; i++) {
            Particion p = tablaParticiones[i];
            
            if (!p.ocupada && p.tamano >= tamanoRequerido) {
                int desperdicio = p.tamano - tamanoRequerido;
                if (desperdicio < menorDesperdicio) {
                    menorDesperdicio = desperdicio;
                    mejorParticion = i;
                }
            }
        }
        
        return mejorParticion;
    }
    
    @Override
    public void liberarEspacio(BCP bcp) {
        if (!bcp.tieneParticionAsignada()) {
            return;
        }
        
        int indiceParticion = bcp.getIndiceParticion();
        
        if (indiceParticion < 0 || indiceParticion >= numParticiones) {
            return;
        }
        
        Particion particion = tablaParticiones[indiceParticion];
        
        // Limpiar instrucciones
        for (int i = 0; i < particion.tamano; i++) {
            memoriaUsuario[particion.inicio + i] = null;
        }
        
        // Liberar partición
        particion.liberar();
        
        System.out.println("[PARTICIONAMIENTO FIJO] Partición " + indiceParticion + " liberada");
    }
    
    @Override
    public void asociarAsignacionAProceso(BCP bcp, InfoAsignacion info) {
        if (info.indiceParticion == null) {
            throw new IllegalArgumentException("InfoAsignacion no contiene índice de partición");
        }
        
        int indiceParticion = info.indiceParticion;
        Particion particion = tablaParticiones[indiceParticion];
        
        // Marcar partición como ocupada
        particion.ocupar(bcp.getIdProceso(), -1); // numeroBCP se actualiza después
        
        // Actualizar BCP
        bcp.asignarParticion(indiceParticion, info.tamanoAsignado);
    }
    
    @Override
    public int getEspacioLibreTotal() {
        int libre = 0;
        for (int i = 0; i < numParticiones; i++) {
            if (!tablaParticiones[i].ocupada) {
                libre += tablaParticiones[i].tamano;
            }
        }
        return libre;
    }
    
    @Override
    public int getFragmentacionInternaTotal() {
        int fragmentacion = 0;
        for (int i = 0; i < numParticiones; i++) {
            Particion p = tablaParticiones[i];
            if (p.ocupada) {
                // La fragmentación se calcula en tiempo de asignación
                // Se necesitaría guardar el tamaño del proceso para calcularlo aquí
                // Por ahora, asumimos que se calcula desde el BCP
            }
        }
        return fragmentacion;
    }
    
    @Override
    public String generarReporte() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ESTRATEGIA: PARTICIONAMIENTO FIJO ").append(tipo).append(" ===\n");
        sb.append(String.format("Número de particiones: %d\n", numParticiones));
        sb.append(String.format("Espacio libre total: %d KB\n", getEspacioLibreTotal()));
        sb.append("\n--- Tabla de Particiones ---\n");
        
        for (int i = 0; i < numParticiones; i++) {
            Particion p = tablaParticiones[i];
            sb.append(String.format("Partición %d: [%d-%d] %d KB - %s%s\n",
                i,
                p.inicio,
                p.inicio + p.tamano - 1,
                p.tamano,
                p.ocupada ? "OCUPADA" : "LIBRE",
                p.ocupada ? " (Proceso ID: " + p.idProceso + ")" : ""
            ));
        }
        
        return sb.toString();
    }
    
    @Override
    public String getNombre() {
        return "Particionamiento Fijo " + tipo;
    }
    
    @Override
    public void reiniciar() {
        // Liberar todas las particiones
        for (int i = 0; i < numParticiones; i++) {
            if (tablaParticiones[i] != null) {
                tablaParticiones[i].liberar();
                
                // Limpiar instrucciones
                Particion p = tablaParticiones[i];
                for (int j = 0; j < p.tamano; j++) {
                    memoriaUsuario[p.inicio + j] = null;
                }
            }
        }
        
        System.out.println("[PARTICIONAMIENTO FIJO] Reiniciado");
    }
    
    // Métodos auxiliares para gestión externa
    
    public void actualizarNumeroBCPEnParticion(int indiceParticion, int numeroBCP) {
        if (indiceParticion >= 0 && indiceParticion < numParticiones) {
            tablaParticiones[indiceParticion].numeroBCP = numeroBCP;
        }
    }
    
    public int getNumParticiones() {
        return numParticiones;
    }
    
    public TipoParticionamiento getTipo() {
        return tipo;
    }
}