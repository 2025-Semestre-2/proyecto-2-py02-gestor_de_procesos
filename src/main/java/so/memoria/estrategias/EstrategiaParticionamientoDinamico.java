package so.memoria.estrategias;

import so.instrucciones.Instruccion;
import so.gestordeprocesos.BCP;
import java.util.*;

/**
 * Implementación de Particionamiento Dinámico usando Buddy System.
 * Gestiona la memoria dividiéndola recursivamente en bloques buddies.
 * 
 * @author dylan
 */
public class EstrategiaParticionamientoDinamico implements IEstrategiaParticionamiento {
    
    private Object[] memoriaUsuario;
    private int tamanoUsuario;
    private int inicioUsuario;
    
    // Constantes Buddy System
    private final int L = 7;  // 2^7 = 128 KB (bloque mínimo)
    private final int U = 13; // 2^13 = 8192 KB (bloque máximo)
    private final int NUM_NIVELES = U - L + 1; // 7 niveles
    private final int TAMANO_BUDDY_USADO = 1 << U; // 8192 KB
    
    // Listas de bloques libres por nivel
    private List<BloqueBuddy>[] listasLibres;
    
    /**
     * Clase interna para representar un bloque buddy
     */
    private static class BloqueBuddy {
        int direccion;
        int tamano;
        int nivel;
        boolean ocupado;
        int idProceso;
        int numeroBCP;
        
        BloqueBuddy(int direccion, int tamano, int nivel) {
            this.direccion = direccion;
            this.tamano = tamano;
            this.nivel = nivel;
            this.ocupado = false;
            this.idProceso = -1;
            this.numeroBCP = -1;
        }
    }
    
    @SuppressWarnings("unchecked")
    public EstrategiaParticionamientoDinamico() {
        this.listasLibres = new ArrayList[NUM_NIVELES];
        for (int i = 0; i < NUM_NIVELES; i++) {
            listasLibres[i] = new ArrayList<>();
        }
    }
    
    @Override
    public void inicializar(Object[] memoriaUsuario, int tamanoUsuario, int inicioUsuario) {
        this.memoriaUsuario = memoriaUsuario;
        this.tamanoUsuario = tamanoUsuario;
        this.inicioUsuario = inicioUsuario;
        
        inicializarBuddySystem();
    }
    
    private void inicializarBuddySystem() {
        // Limpiar listas
        for (int i = 0; i < NUM_NIVELES; i++) {
            listasLibres[i].clear();
        }
        
        // Crear bloque inicial del tamaño máximo
        BloqueBuddy bloqueInicial = new BloqueBuddy(inicioUsuario, TAMANO_BUDDY_USADO, U);
        listasLibres[U - L].add(bloqueInicial);
        
        System.out.println("[BUDDY SYSTEM] Inicializado:");
        System.out.println("  - Tamaño total: " + TAMANO_BUDDY_USADO + " KB");
        System.out.println("  - Bloque mínimo: " + (1 << L) + " KB (2^" + L + ")");
        System.out.println("  - Bloque máximo: " + (1 << U) + " KB (2^" + U + ")");
        System.out.println("  - Niveles: " + NUM_NIVELES + " (" + L + " a " + U + ")");
    }
    
    @Override
    public InfoAsignacion cargarInstrucciones(Instruccion[] instrucciones) {
        int tamanoRequerido = instrucciones.length;
        
        BloqueBuddy bloque = asignarBuddy(tamanoRequerido);
        
        if (bloque == null) {
            System.out.println("[BUDDY SYSTEM] No hay espacio para " + tamanoRequerido + " instrucciones");
            return null;
        }
        
        // Cargar instrucciones en el bloque
        for (int i = 0; i < instrucciones.length; i++) {
            memoriaUsuario[bloque.direccion + i] = instrucciones[i];
        }
        
        int fragmentacionInterna = bloque.tamano - tamanoRequerido;
        
        System.out.println("[BUDDY SYSTEM] Instrucciones cargadas:");
        System.out.println("  - Dirección: " + bloque.direccion);
        System.out.println("  - Bloque: " + bloque.tamano + " KB (nivel " + bloque.nivel + ")");
        System.out.println("  - Usado: " + tamanoRequerido + " KB");
        System.out.println("  - Fragmentación interna: " + fragmentacionInterna + " KB");
        
        InfoAsignacion info = new InfoAsignacion(bloque.direccion, bloque.tamano, fragmentacionInterna);
        info.nivelBuddy = bloque.nivel;
        info.direccionBloque = bloque.direccion;
        
        return info;
    }
    
    /**
     * Asigna un bloque usando el algoritmo Buddy System
     */
    private BloqueBuddy asignarBuddy(int tamanoRequerido) {
        int nivelRequerido = encontrarNivelAdecuado(tamanoRequerido);
        
        if (nivelRequerido < L || nivelRequerido > U) {
            System.out.println("[BUDDY] Tamaño " + tamanoRequerido + " KB fuera de rango");
            return null;
        }
        
        System.out.println("[BUDDY] Solicitando " + tamanoRequerido + " KB → Nivel " + 
                         nivelRequerido + " (" + (1 << nivelRequerido) + " KB)");
        
        BloqueBuddy bloque = obtenerBloque(nivelRequerido);
        
        if (bloque != null) {
            bloque.ocupado = true;
            System.out.println("[BUDDY] ✓ Bloque asignado: " + bloque.tamano + 
                             " KB en dirección " + bloque.direccion);
        } else {
            System.out.println("[BUDDY] ✗ No hay bloques disponibles");
        }
        
        return bloque;
    }
    
    /**
     * Algoritmo recursivo para obtener un bloque
     */
    private BloqueBuddy obtenerBloque(int i) {
        if (i > U) {
            return null; // Fallo
        }
        
        // Buscar bloque en la lista del nivel i
        BloqueBuddy bloque = removerBloqueLibreDeLista(i);
        
        if (bloque != null) {
            return bloque; // Encontrado
        }
        
        // No hay bloque en nivel i, buscar en nivel superior
        BloqueBuddy bloqueGrande = obtenerBloque(i + 1);
        
        if (bloqueGrande == null) {
            return null; // No hay bloques disponibles
        }
        
        // Dividir el bloque en dos buddies
        BloqueBuddy buddy1 = new BloqueBuddy(bloqueGrande.direccion, 1 << i, i);
        BloqueBuddy buddy2 = new BloqueBuddy(bloqueGrande.direccion + (1 << i), 1 << i, i);
        
        // Agregar el segundo buddy a la lista del nivel i
        listasLibres[i - L].add(buddy2);
        
        System.out.println("[BUDDY] División: Bloque de " + bloqueGrande.tamano + 
                         " KB → 2 bloques de " + (1 << i) + " KB");
        
        return buddy1;
    }
    
    @Override
    public void liberarEspacio(BCP bcp) {
        if (!bcp.tieneBloqueBuddyAsignado()) {
            return;
        }
        
        int direccion = bcp.getDireccionBloque();
        int tamano = bcp.getTamanoBloqueAsignado();
        int nivel = bcp.getNivelBuddy();
        
        // Limpiar instrucciones
        for (int i = 0; i < tamano; i++) {
            if (direccion + i < memoriaUsuario.length) {
                memoriaUsuario[direccion + i] = null;
            }
        }
        
        // Crear bloque y liberarlo
        BloqueBuddy bloque = new BloqueBuddy(direccion, tamano, nivel);
        liberarBuddy(bloque);
        
        System.out.println("[BUDDY SYSTEM] Bloque liberado: " + tamano + " KB en dirección " + direccion);
    }
    
    private void liberarBuddy(BloqueBuddy bloque) {
        if (bloque == null) return;
        
        bloque.ocupado = false;
        bloque.idProceso = -1;
        bloque.numeroBCP = -1;
        
        // Intentar coalescing
        coalescing(bloque);
    }
    
    /**
     * Realiza coalescing (fusión) de buddies
     */
    private void coalescing(BloqueBuddy bloque) {
        if (bloque.nivel >= U) {
            // Ya es el bloque más grande
            listasLibres[bloque.nivel - L].add(bloque);
            return;
        }
        
        // Calcular la dirección del buddy
        int direccionBuddy = calcularDireccionBuddy(bloque.direccion, bloque.nivel);
        
        // Buscar el buddy en la lista del mismo nivel
        BloqueBuddy buddy = buscarYRemoverBuddy(direccionBuddy, bloque.nivel);
        
        if (buddy != null && !buddy.ocupado) {
            // El buddy está libre, fusionar
            int nuevaDireccion = Math.min(bloque.direccion, buddy.direccion);
            int nuevoNivel = bloque.nivel + 1;
            
            BloqueBuddy bloqueGrande = new BloqueBuddy(nuevaDireccion, 1 << nuevoNivel, nuevoNivel);
            
            System.out.println("[BUDDY] Coalescing: 2 bloques de " + bloque.tamano + 
                             " KB → 1 bloque de " + bloqueGrande.tamano + " KB");
            
            // Recursivamente intentar más coalescing
            coalescing(bloqueGrande);
        } else {
            // No se puede fusionar, agregar a la lista
            listasLibres[bloque.nivel - L].add(bloque);
        }
    }
    
    private int encontrarNivelAdecuado(int tamano) {
        for (int i = L; i <= U; i++) {
            if (tamano <= (1 << i)) {
                return i;
            }
        }
        return U + 1;
    }
    
    private int calcularDireccionBuddy(int direccion, int nivel) {
        int tamanoBloque = 1 << nivel;
        int offset = direccion - inicioUsuario;
        int offsetBuddy = offset ^ tamanoBloque;
        return inicioUsuario + offsetBuddy;
    }
    
    private BloqueBuddy removerBloqueLibreDeLista(int nivel) {
        List<BloqueBuddy> lista = listasLibres[nivel - L];
        
        if (!lista.isEmpty()) {
            return lista.remove(0);
        }
        
        return null;
    }
    
    private BloqueBuddy buscarYRemoverBuddy(int direccion, int nivel) {
        List<BloqueBuddy> lista = listasLibres[nivel - L];
        
        for (int i = 0; i < lista.size(); i++) {
            BloqueBuddy bloque = lista.get(i);
            if (bloque.direccion == direccion) {
                lista.remove(i);
                return bloque;
            }
        }
        
        return null;
    }
    
    @Override
    public void asociarAsignacionAProceso(BCP bcp, InfoAsignacion info) {
        if (info.nivelBuddy == null || info.direccionBloque == null) {
            throw new IllegalArgumentException("InfoAsignacion no contiene datos de buddy");
        }
        
        bcp.asignarBloqueBuddy(-1, info.tamanoAsignado, info.nivelBuddy, info.direccionBloque);
    }
    
    @Override
    public int getEspacioLibreTotal() {
        int libre = 0;
        for (int nivel = L; nivel <= U; nivel++) {
            List<BloqueBuddy> lista = listasLibres[nivel - L];
            for (BloqueBuddy bloque : lista) {
                if (!bloque.ocupado) {
                    libre += bloque.tamano;
                }
            }
        }
        return libre;
    }
    
    @Override
    public int getFragmentacionInternaTotal() {
        // La fragmentación se calcula desde los BCPs
        // Aquí retornamos 0 porque no tenemos acceso directo a los BCPs
        return 0;
    }
    
    @Override
    public String generarReporte() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ESTRATEGIA: BUDDY SYSTEM ===\n");
        sb.append(String.format("Niveles: %d a %d (%d-%d KB)\n", L, U, (1<<L), (1<<U)));
        sb.append(String.format("Espacio libre total: %d KB\n", getEspacioLibreTotal()));
        sb.append("\n--- Listas de Bloques Libres ---\n");
        
        for (int nivel = L; nivel <= U; nivel++) {
            List<BloqueBuddy> lista = listasLibres[nivel - L];
            int count = lista.size();
            
            if (count > 0) {
                sb.append(String.format("Nivel %d (%d KB): %d bloques libres\n", 
                    nivel, (1 << nivel), count));
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public String getNombre() {
        return "Particionamiento Dinámico (Buddy System)";
    }
    
    @Override
    public void reiniciar() {
        // Limpiar todas las listas
        for (int i = 0; i < NUM_NIVELES; i++) {
            listasLibres[i].clear();
        }
        
        // Reinicializar con bloque completo
        inicializarBuddySystem();
        
        System.out.println("[BUDDY SYSTEM] Reiniciado");
    }
}