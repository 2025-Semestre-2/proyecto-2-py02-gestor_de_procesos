package so.memoria;

import java.util.ArrayList;
import java.util.List;

/**
 * Simula la memoria secundaria del sistema (almacenamiento persistente).
 * 
 * Cada programa se carga línea por línea y se registra con metadatos:
 * nombre, posición de inicio y tamaño.
 * 
 * @author dylan
 */
public class MemoriaSecundaria {

    private final int tamanoTotal;
    private final int TamanoMemVirtual;
    private final Object[] almacenamiento;

    public MemoriaSecundaria() {
        this(512, 64);
    }

    public MemoriaSecundaria(int tamanoTotal, int memoriaVirtual) {
        if (memoriaVirtual >= tamanoTotal) {
            throw new IllegalArgumentException(
                "Error: la memoria virtual no puede ser mayor o igual al tamaño total del almacenamiento."
            );
        }

        this.tamanoTotal = tamanoTotal;
        this.TamanoMemVirtual = memoriaVirtual;
        this.almacenamiento = new Object[tamanoTotal];

        inicializarAlmacenamiento();
    }

    /**
     * Inicializa el almacenamiento con valores nulos.
     */
    private void inicializarAlmacenamiento() {
        for (int i = 0; i < tamanoTotal; i++) {
            almacenamiento[i] = null;
        }
    }

    /**
     * Carga uno o varios programas en la memoria secundaria.
     *
     * @param nombres nombres de los programas
     * @param programas lista de programas (cada uno con sus líneas ASM)
     */
    public void cargarProgramas(String[] nombres, List<String>[] programas) {
        if (nombres == null || programas == null) {
            throw new IllegalArgumentException("Los nombres y programas no pueden ser nulos.");
        }
        if (nombres.length != programas.length) {
            throw new IllegalArgumentException("La cantidad de nombres y programas no coincide.");
        }

        // Limpieza de la memoria
        for (int i = 0; i < tamanoTotal; i++) {
            almacenamiento[i] = null;
        }

        int limiteAlmacenamiento = tamanoTotal - TamanoMemVirtual;
        int espacioDisponible = limiteAlmacenamiento;
        int indiceInicio = nombres.length; // deja espacio para las cabeceras
        List<String> nombresValidos = new ArrayList<>();
        List<List<String>> programasValidos = new ArrayList<>();

        // Verificar espacio suficiente para cada programa
        for (int i = 0; i < nombres.length; i++) {
            List<String> lineas = programas[i];
            if (lineas.size() <= espacioDisponible) {
                nombresValidos.add(nombres[i]);
                programasValidos.add(lineas);
                espacioDisponible -= lineas.size();
            } else {
                System.err.println("No hay suficiente espacio para el programa " + nombres[i] + ", se omite.");
                indiceInicio--;
            }
        }

        // Cargar programas en memoria
        for (int i = 0; i < nombresValidos.size(); i++) {
            String nombrePrograma = nombresValidos.get(i);
            List<String> lineas = programasValidos.get(i);

            // Cabecera: "nombre;inicio;tamaño"
            almacenamiento[i] = nombrePrograma + ";" + indiceInicio + ";" + lineas.size();

            for (String linea : lineas) {
                almacenamiento[indiceInicio++] = linea;
            }
        }
    }

    /**
     * Lee un programa completo desde la memoria secundaria por nombre.
     *
     * @param nombre nombre del programa
     * @return lista con las líneas del programa o null si no se encuentra
     */
    public List<String> leerPrograma(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            throw new IllegalArgumentException("El nombre del programa no puede estar vacío.");
        }

        for (int i = 0; i < tamanoTotal - TamanoMemVirtual; i++) {
            Object celda = almacenamiento[i];
            if (celda instanceof String str && str.startsWith(nombre + ";")) {
                String[] partes = str.split(";");
                if (partes.length != 3) {
                    throw new IllegalStateException("Formato de metadatos inválido en la celda " + i);
                }

                int lineaInicio = Integer.parseInt(partes[1]);
                int longitud = Integer.parseInt(partes[2]);

                List<String> programa = new ArrayList<>();

                for (int j = 0; j < longitud; j++) {
                    int direccion = lineaInicio + j;
                    if (direccion >= almacenamiento.length) break;
                    Object linea = almacenamiento[direccion];
                    if (linea instanceof String s) {
                        programa.add(s);
                    }
                }
                return programa;
            }
        }
        return null;
    }

    /**
     * Muestra el contenido de la memoria secundaria.
     */
    public void mostrarAlmacenamiento() {
        for (int i = 0; i < almacenamiento.length; i++) {
            Object valor = almacenamiento[i];
            if (valor != null) {
                System.out.println("[" + i + "] " + valor);
            } else {
                System.out.println("[" + i + "] 0");
            }
        }
    }

    // Getters

    public int getTamanoTotal() {
        return tamanoTotal;
    }

    public int getMemoriaVirtual() {
        return TamanoMemVirtual;
    }

    public Object[] getAlmacenamiento() {
        return almacenamiento;
    }
}
