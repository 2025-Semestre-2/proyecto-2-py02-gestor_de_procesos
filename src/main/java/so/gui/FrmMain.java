package so.gui;

import java.awt.Color;
import java.awt.Component;
import javax.swing.table.DefaultTableModel;
import so.main.SistemaOperativoV2;
import so.planificacion.PlanificadorFIFO;
import so.planificacion.PlanificadorSJF;
import so.planificacion.IPlanificador;
import java.util.List;
import java.util.ArrayList;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import so.gestordeprocesos.BCP;
import so.instrucciones.Instruccion;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import so.planificacion.PlanificadorHRRN;

/**
 *
 * @author dylan
 */
public class FrmMain extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FrmMain.class.getName());
    
    private SistemaOperativoV2 sistemaOperativo;
    
    private final Map<Integer, Color> mapaColoresSecundaria = new HashMap<>();    
    private final Map<Integer, Color> mapaColoresPrincipal = new HashMap<>();
    private final Random random = new Random();
    
    // Timer para ejecución automática
    private Timer timerEjecucion;
    private boolean ejecutandoAutomatico = false;    
    
    /**
     * Creates new form FrmMain
     */
    public FrmMain() {
        this(null);
    }
    
    public FrmMain(SistemaOperativoV2 sistemaOperativo) {        
        this.sistemaOperativo = sistemaOperativo;
        
        if (this.sistemaOperativo == null) {
            crearSistemaPrueba();
        }           
        
        initComponents();
        inicializarComponentes();

        this.setSize(1500, 800);
        this.setResizable(false);        
        this.setLocationRelativeTo(null);
    }

    private void crearSistemaPrueba() {
        try {
            // Configuración de prueba
            int tamanoMemSecundaria = 164;
            int tamanoMemVirtual = 64;
            int tamanoMemUsuario = 1000;
            int cantidadCPUs = 1;
            
            // Estrategia de memoria (Particionamiento Fijo con particiones de 100 KB)
            String estrategiaMemoria = "DINAMICO";
            Object configEstrategia = 50;
            
            
            // Planificadores (FIFO para CPU 0, SJF para CPU 1)
            IPlanificador[] planificadores = new IPlanificador[] {
                new PlanificadorFIFO()              
            };
            
            // Crear sistema operativo
            this.sistemaOperativo = new SistemaOperativoV2(
                tamanoMemSecundaria,
                tamanoMemVirtual,
                tamanoMemUsuario,
                estrategiaMemoria,
                configEstrategia,
                cantidadCPUs,
                planificadores
            );
            
            System.out.println("[FrmMain] Sistema de prueba creado exitosamente");
            System.out.println("  - CPUs: " + cantidadCPUs);
            System.out.println("  - Estrategia: " + estrategiaMemoria);
            
        } catch (Exception e) {
            System.err.println("[FrmMain] Error al crear sistema de prueba: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void inicializarComponentes() {
        inicializarJTable(jTable_memoriaSecundaria);
        inicializarJTable(jTable_memoriaPrincipal);
        inicializarTablaInfoProcesos();
        actualizarPanelesCPU();
        configurarBotones();
        inicializarTimer();
    }    

    private void configurarListeners() {
        // Agregar listener al sistema operativo
        sistemaOperativo.addListener(new SistemaOperativoV2.SistemaListener() {
            @Override
            public void onProcesoEjecutado(int cpu, BCP bcp) {
                SwingUtilities.invokeLater(() -> {
                    actualizarPanelCPU(cpu);
                    actualizarTablaInfoProcesos();
                });
            }
            
            @Override
            public void onProcesoFinalizado(int cpu, BCP bcp) {
                SwingUtilities.invokeLater(() -> {
                    actualizarPanelCPU(cpu);
                    actualizarTablaInfoProcesos();
                    actualizarTablaMemoriaPrincipal();
                    panelConsola.escribir("✓ Proceso finalizado: " + bcp.getNombreProceso() + " [CPU " + cpu + "]");
                    
                    // Si no hay más procesos, detener ejecución automática
                    if (ejecutandoAutomatico && !sistemaOperativo.hayProcesosPorEjecutar()) {
                        detenerEjecucionAutomatica();
                        panelConsola.escribir("✓ Todos los procesos han finalizado");
                    }
                });
            }
            
            @Override
            public void onProcesoNuevo(BCP bcp) {
                SwingUtilities.invokeLater(() -> {
                    actualizarTablaInfoProcesos();
                    actualizarTablaMemoriaPrincipal();
                });
            }
            
            @Override
            public void onEstadoCambiado(boolean activo, boolean pausado) {
                SwingUtilities.invokeLater(() -> {
                    actualizarEstadoBotones();
                });
            }
        });
    }
    
    private void inicializarTimer() {
        // Timer para ejecución automática (1 segundo por instrucción)
        timerEjecucion = new Timer(1000, e -> {
            ejecutarCicloAutomatico();
        });
    }    
    
    private void configurarBotones() {
        // Botón Cargar Archivos
        jButton_cargarArchivos.addActionListener(e -> cargarArchivosAction());       
        
        // Botón Iniciar (cargar a memoria principal)
        jButton_iniciarPrograma.addActionListener(e -> cargarMemoriaPrincipalAction());

        // Botón Ejecutar (automático)
        jButton_ejecutarAutomatico.addActionListener(e -> toggleEjecucionAutomatica());
        
        // Botón Paso a Paso
        jButton_pasoApaso.addActionListener(e -> ejecutarPasoAPasoAction());        
    }    

    private void cargarArchivosAction() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Seleccionar archivos de programa");
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new FileNameExtensionFilter("Archivos de texto (*.asm)", "asm"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] archivos = fileChooser.getSelectedFiles();
            cargarArchivosSeleccionados(archivos);
        }
    }
    
    private void cargarArchivosSeleccionados(File[] archivos) {
        try {
            String[] nombres = new String[archivos.length];
            List<String>[] programas = new ArrayList[archivos.length];
            
            for (int i = 0; i < archivos.length; i++) {
                File archivo = archivos[i];
                String nombre = archivo.getName().replace(".asm", "");
                nombres[i] = nombre;
                programas[i] = leerArchivoPrograma(archivo);
                
                panelConsola.escribir("Cargando: " + nombre + " (" + programas[i].size() + " líneas)");
            }
            
            // Cargar a memoria secundaria
            boolean exito = sistemaOperativo.cargarArchivosMemoriaSecundaria(nombres, programas);
            
            if (exito) {
                actualizarTablaMemoriaSecundaria();
                
            } else {
                panelConsola.escribirError("✗ Error al cargar archivos");
            }
            
        } catch (Exception e) {
            panelConsola.escribirError("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<String> leerArchivoPrograma(File archivo) {
        List<String> lineas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                lineas.add(linea.trim());
            }
        } catch (Exception e) {
            panelConsola.escribirError("✗ Error leyendo archivo: " + archivo.getName());
        }
        return lineas;
    }

    private void actualizarTablaMemoriaSecundaria() {
        Object[] contenido = sistemaOperativo.getMemoriaSecundaria().getAlmacenamiento();
        DefaultTableModel modelo = (DefaultTableModel) jTable_memoriaSecundaria.getModel();
        modelo.setRowCount(0);
        mapaColoresSecundaria.clear();
        
        List<int[]> bloques = new ArrayList<>();
        for (int i = 0; i < contenido.length; i++) {
            Object celda = contenido[i];
            modelo.addRow(new Object[]{i, celda == null ? "0" : celda.toString()});

            if (celda instanceof String str && str.contains(";")) {
                String[] partes = str.split(";");
                if (partes.length == 3) {
                    try {
                        int inicio = Integer.parseInt(partes[1]);
                        int largo = Integer.parseInt(partes[2]);
                        bloques.add(new int[]{i, inicio, largo});
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        Random random = new Random();
        for (int[] bloque : bloques) {
            Color color = new Color(180 + random.nextInt(75), 180 + random.nextInt(75), 180 + random.nextInt(75));
            mapaColoresSecundaria.put(bloque[0], color);
            for (int j = bloque[1]; j < bloque[1] + bloque[2] && j < contenido.length; j++) {
                mapaColoresSecundaria.put(j, color);
            }
        }

        jTable_memoriaSecundaria.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Color color = mapaColoresSecundaria.get(row);
                c.setBackground(color != null ? color : Color.WHITE);
                c.setForeground(Color.BLACK);
                return c;
            }
        });
        jTable_memoriaSecundaria.repaint();
    }
    
    private void cargarMemoriaPrincipalAction() {
        try {
            if (sistemaOperativo == null) {
                panelConsola.escribirError("✗ No hay sistema operativo configurado");
                return;
            }

            panelConsola.escribir("Cargando programas a memoria principal...");

            // Cargar programas a memoria principal
            int programasCargados = sistemaOperativo.cargarProgramasMemoriaPrincipal();

            if (programasCargados > 0) {
                panelConsola.escribir("✓ " + programasCargados + " programas cargados a memoria principal");

                // Actualizar visualización de memoria principal
                actualizarTablaMemoriaPrincipal();

                // Actualizar tabla de información de procesos
                actualizarTablaInfoProcesos();                

                // Mostrar información de BCPs cargados
                mostrarInfoBCPs();

            } else {
                panelConsola.escribir("ℹ No se pudieron cargar programas a memoria principal");
                panelConsola.escribir("  - Puede que no haya programas pendientes o memoria llena");
            }

        } catch (Exception e) {
            panelConsola.escribirError("✗ Error al cargar memoria principal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método para actualizar tabla de memoria principal con colores
    private void actualizarTablaMemoriaPrincipal() {
        Object[] contenido = sistemaOperativo.getMemoriaPrincipal().getMemoriaCompleta();
        DefaultTableModel modelo = (DefaultTableModel) jTable_memoriaPrincipal.getModel();
        modelo.setRowCount(0);
        mapaColoresPrincipal.clear();

        // Obtener información de los BCPs cargados
        List<BCP> bcps = sistemaOperativo.getBCPsCargados();

        // Generar colores para cada BCP
        Map<Integer, Color> coloresBCP = new HashMap<>();
        for (BCP bcp : bcps) {
            Color color = generarColorUnico();
            coloresBCP.put(bcp.getIdProceso(), color);
        }

        // Identificar áreas de memoria ocupadas por cada BCP
        for (BCP bcp : bcps) {
            Color color = coloresBCP.get(bcp.getIdProceso());
            int direccionBase = bcp.getDireccionBase();
            int tamanoProceso = bcp.getTamanoProceso();

            // Marcar el área del proceso
            for (int i = 0; i < tamanoProceso && (direccionBase + i) < contenido.length; i++) {
                mapaColoresPrincipal.put(direccionBase + i, color);
            }

            // También marcar la posición del BCP en el área del SO
            int indiceBCP = encontrarIndiceBCP(bcp);
            if (indiceBCP >= 0) {
                // Marcar todas las posiciones del BCP en la tabla del SO
                for (int i = 0; i < 32; i++) { // Tamaño de BCP es 32 atributos
                    mapaColoresPrincipal.put(indiceBCP + i, color);
                }
            }
        }

        // Llenar la tabla
        for (int i = 0; i < contenido.length; i++) {
            Object celda = contenido[i];
            String valor = "0";

            if (celda != null) {
                if (celda instanceof Instruccion) {
                    valor = celda.toString();
                } else if (celda instanceof String) {
                    valor = (String) celda;
                } else if (celda instanceof Integer) {
                    valor = celda.toString();
                } else {
                    valor = "[OBJ]";
                }
            }

            modelo.addRow(new Object[]{i, valor});
        }

        // Aplicar colores
        jTable_memoriaPrincipal.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Color color = mapaColoresPrincipal.get(row);
                c.setBackground(color != null ? color : Color.WHITE);
                c.setForeground(Color.BLACK);
                return c;
            }
        });

        jTable_memoriaPrincipal.repaint();
        panelConsola.escribir("✓ Memoria principal actualizada con " + bcps.size() + " BCPs");
    }

    // Método auxiliar para encontrar el índice del BCP en memoria
    private int encontrarIndiceBCP(BCP bcp) {
        // En MemoriaPrincipalV2, los BCPs empiezan en la posición 10
        // y cada BCP ocupa 32 posiciones
        for (int i = 0; i < sistemaOperativo.getMemoriaPrincipal().getMaxProcesos(); i++) {
            BCP bcpEnMemoria = sistemaOperativo.getMemoriaPrincipal().obtenerBCP(i);
            if (bcpEnMemoria != null && bcpEnMemoria.getIdProceso() == bcp.getIdProceso()) {
                return 10 + (i * 32); // IDX_PRIMER_BCP + (numeroBCP * TAMANO_BCP)
            }
        }
        return -1;
    }

    // Método para generar colores únicos
    private Color generarColorUnico() {
        // Generar colores pastel para mejor visualización
        int r = 150 + random.nextInt(100);
        int g = 150 + random.nextInt(100);
        int b = 150 + random.nextInt(100);
        return new Color(r, g, b);
    }

    // Método para mostrar información de BCPs en consola
    private void mostrarInfoBCPs() {
        List<BCP> bcps = sistemaOperativo.getBCPsCargados();

        panelConsola.escribir("--- BCPs Cargados en Memoria Principal ---");
        for (BCP bcp : bcps) {
            String infoParticion = bcp.tieneParticionAsignada() ? 
                bcp.getInfoParticion() : "Sin partición asignada";

            panelConsola.escribir(String.format(
                "• %s (ID: %d) - PC: %d/%d - %s",
                bcp.getNombreProceso(),
                bcp.getIdProceso(),
                bcp.getPC(),
                bcp.getTamanoProceso(),
                infoParticion
            ));
        }
        panelConsola.escribir("------------------------------------------");
    }   

    // Nuevo método para actualizar la tabla de información de procesos
    private void actualizarTablaInfoProcesos() {
        if (sistemaOperativo == null) return;

        DefaultTableModel modelo = (DefaultTableModel) jTable_infoProcesos.getModel();
        modelo.setRowCount(0); // Limpiar tabla

        List<Object[]> datosProcesos = sistemaOperativo.getInformacionProcesosParaTabla();

        for (Object[] fila : datosProcesos) {
            // Solo usar las primeras 6 columnas: Proceso, Ráfaga, T. Llegada, CPU, Estado, T. Restante
            Object[] filaTabla = new Object[]{
                fila[0], // Proceso
                fila[1], // Ráfaga
                fila[2], // T. Llegada
                fila[3], // CPU
                fila[4], // Estado
                fila[5]  // T. Restante
            };
            modelo.addRow(filaTabla);
        }

        // Actualizar estadísticas en la consola
        panelConsola.escribir("✓ Tabla de procesos actualizada: " + datosProcesos.size() + " procesos");

        // Mostrar resumen por CPU
        mostrarResumenCPUs();
    }

    // Método para mostrar resumen de distribución por CPUs
    private void mostrarResumenCPUs() {
        if (sistemaOperativo == null) return;

        Map<Integer, Integer> conteoPorCPU = new HashMap<>();
        List<Object[]> datosProcesos = sistemaOperativo.getInformacionProcesosParaTabla();

        for (Object[] fila : datosProcesos) {
            Integer cpu = (Integer) fila[3];
            conteoPorCPU.put(cpu, conteoPorCPU.getOrDefault(cpu, 0) + 1);
        }

        panelConsola.escribir("--- Distribución por CPUs ---");
        for (Map.Entry<Integer, Integer> entry : conteoPorCPU.entrySet()) {
            panelConsola.escribir("  CPU " + entry.getKey() + ": " + entry.getValue() + " procesos");
        }
        panelConsola.escribir("-----------------------------");
    }    
    
    // Nuevo método para inicializar la tabla de información de procesos
    private void inicializarTablaInfoProcesos() {
        DefaultTableModel modelo = new DefaultTableModel(
            new Object[][]{},
            new String[]{"Proceso", "Ráfaga", "T. Llegada", "CPU", "Estado", "T. Restante"}
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 1, 2, 3, 5 -> Integer.class; // Ráfaga, T. Llegada, CPU, T. Restante son números
                    default -> String.class;
                };
            }
        };

        jTable_infoProcesos.setModel(modelo);
        jTable_infoProcesos.getTableHeader().setResizingAllowed(false);
        jTable_infoProcesos.getTableHeader().setReorderingAllowed(false);

        // Ajustar ancho de columnas
        
        jTable_infoProcesos.getColumnModel().getColumn(0).setPreferredWidth(155); // Proceso
        jTable_infoProcesos.getColumnModel().getColumn(1).setPreferredWidth(60);  // Ráfaga
        jTable_infoProcesos.getColumnModel().getColumn(2).setPreferredWidth(80);  // T. Llegada
        jTable_infoProcesos.getColumnModel().getColumn(3).setPreferredWidth(40);  // CPU
        jTable_infoProcesos.getColumnModel().getColumn(4).setPreferredWidth(80);  // Estado
        jTable_infoProcesos.getColumnModel().getColumn(5).setPreferredWidth(85);  // T. Restante
        
        // Hacer que las columnas no sean redimensionables
        for (int i = 0; i < jTable_infoProcesos.getColumnCount(); i++) {
            jTable_infoProcesos.getColumnModel().getColumn(i).setResizable(false);
        }

        // Agregar renderizador para colorear por CPU
        jTable_infoProcesos.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                // Colorear según el CPU asignado
                if (column == 3 && value instanceof Integer cpu) { // Columna CPU
                    Color color = getColorForCPU(cpu);
                    c.setBackground(color);
                    c.setForeground(Color.BLACK);
                } else if (column == 4) { // Columna Estado
                    String estado = value.toString();
                    Color color = getColorForEstado(estado);
                    c.setBackground(color);
                    c.setForeground(Color.BLACK);
                } else {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }

                return c;
            }
        });
    }

    // Método para obtener color según CPU
    private Color getColorForCPU(int cpu) {
        return switch (cpu) {
            case 0 -> new Color(200, 230, 255); // Azul claro
            case 1 -> new Color(200, 255, 230); // Verde claro
            case 2 -> new Color(255, 230, 200); // Naranja claro
            case 3 -> new Color(230, 200, 255); // Violeta claro
            default -> new Color(240, 240, 240); // Gris claro
        };
    }

    // Método para obtener color según estado
    private Color getColorForEstado(String estado) {
        return switch (estado.toUpperCase()) {
            case "NUEVO" -> new Color(255, 255, 200); // Amarillo claro
            case "LISTO" -> new Color(200, 255, 200); // Verde claro
            case "EJECUCIÓN" -> new Color(255, 200, 200); // Rojo claro
            case "FINALIZADO" -> new Color(200, 200, 200); // Gris
            default -> Color.WHITE;
        };
    }
    
    private void inicializarJTable(javax.swing.JTable jTable) {
        DefaultTableModel modelo = new DefaultTableModel(
            new Object[][]{},
            new String[]{"Posición", "Valor"}
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        jTable.setModel(modelo);
        jTable.getTableHeader().setResizingAllowed(false);
        jTable.getTableHeader().setReorderingAllowed(false);
        jTable.setSize(250, 400);       

        // Ajustar ancho de columnas
        jTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        jTable.getColumnModel().getColumn(1).setPreferredWidth(170);

        jTable.getColumnModel().getColumn(0).setResizable(false);
        jTable.getColumnModel().getColumn(1).setResizable(false);
    }

    private void actualizarPanelesCPU() {
        // Guardar en arreglos para iterar fácilmente
        javax.swing.JLabel[] labels = { jLabel_CPU1, jLabel_CPU2, jLabel_CPU3, jLabel_CPU4, jLabel_CPU5 };
        javax.swing.JScrollPane[] areas = { jScrollPane3, jScrollPane4, jScrollPane5, jScrollPane6, jScrollPane7 };

        // Obtener cantidad de CPUs configurada en el sistema
        int cantidadCPUs = sistemaOperativo.getCantidadCPUs();

        // Mostrar solo los necesarios
        for (int i = 0; i < labels.length; i++) {
            boolean visible = i < cantidadCPUs;
            labels[i].setVisible(visible);
            areas[i].setVisible(visible);
        }

        // Forzar actualización visual
        this.revalidate();
        this.repaint();
    }

   
    // ========== MÉTODOS DE EJECUCIÓN ==========
    
    /**
     * Toggle ejecución automática (iniciar/pausar)
     */
    private void toggleEjecucionAutomatica() {
        if (ejecutandoAutomatico) {
            pausarEjecucionAutomatica();
        } else {
            iniciarEjecucionAutomatica();
        }
    }
    
    /**
     * Inicia la ejecución automática
     */
    private void iniciarEjecucionAutomatica() {
        if (!sistemaOperativo.hayProcesosPorEjecutar()) {
            panelConsola.escribirError("✗ No hay procesos para ejecutar");
            return;
        }
        
        ejecutandoAutomatico = true;
        sistemaOperativo.iniciarEjecucionAutomatica();
        timerEjecucion.start();
        
        jButton_ejecutarAutomatico.setText("Pausar");
        jButton_pasoApaso.setEnabled(false);
        jButton_iniciarPrograma.setEnabled(false);
        
        panelConsola.escribir("▶ Ejecución automática iniciada");
    }
    
    /**
     * Pausa la ejecución automática
     */
    private void pausarEjecucionAutomatica() {
        timerEjecucion.stop();
        ejecutandoAutomatico = false;
        sistemaOperativo.pausarEjecucionAutomatica();
        
        jButton_ejecutarAutomatico.setText("Reanudar");
        jButton_pasoApaso.setEnabled(true);
        jButton_iniciarPrograma.setEnabled(true);
        
        panelConsola.escribir("⏸ Ejecución automática pausada");
    }
    
    /**
     * Detiene completamente la ejecución automática
     */
    private void detenerEjecucionAutomatica() {
        timerEjecucion.stop();
        ejecutandoAutomatico = false;
        sistemaOperativo.detenerEjecucionAutomatica();
        
        jButton_ejecutarAutomatico.setText("Ejecutar");
        jButton_pasoApaso.setEnabled(true);
        jButton_iniciarPrograma.setEnabled(true);
        
        panelConsola.escribir("⏹ Ejecución automática detenida");
        
        // Limpiar paneles de CPU
        for (int i = 0; i < sistemaOperativo.getCantidadCPUs(); i++) {
            actualizarPanelCPU(i);
        }
    }
    
    /**
     * Ejecuta un ciclo automático (llamado por el timer)
     */
    private void ejecutarCicloAutomatico() {
        try {
            boolean seEjecutoAlgo = sistemaOperativo.ejecutarPasoAPaso();
            // Actualizar todos los paneles
            for (int i = 0; i < sistemaOperativo.getCantidadCPUs(); i++) {
                actualizarPanelCPU(i);
            }
            actualizarTablaInfoProcesos();
            
            if (!seEjecutoAlgo && !sistemaOperativo.hayProcesosPorEjecutar()) {
                detenerEjecucionAutomatica();
                panelConsola.escribir("✓ Todos los procesos han finalizado");
            }
            
        } catch (Exception e) {
            panelConsola.escribirError("✗ Error en ejecución: " + e.getMessage());
            detenerEjecucionAutomatica();
        }
    }
    
    /**
     * Ejecuta un paso (una instrucción por CPU)
     */
    private void ejecutarPasoAPasoAction() {
        if (ejecutandoAutomatico) {
            panelConsola.escribirError("✗ Detenga la ejecución automática primero");
            return;
        }
        
        if (!sistemaOperativo.hayProcesosPorEjecutar()) {
            panelConsola.escribirError("✗ No hay procesos para ejecutar");
            return;
        }
        
        try {
            boolean seEjecutoAlgo = sistemaOperativo.ejecutarPasoAPaso();
            
            if (seEjecutoAlgo) {
                panelConsola.escribir("→ Paso ejecutado");
            } else {
                panelConsola.escribir("ℹ No hay procesos listos para ejecutar");
            }
            
            // Actualizar todos los paneles
            for (int i = 0; i < sistemaOperativo.getCantidadCPUs(); i++) {
                actualizarPanelCPU(i);
            }
            actualizarTablaInfoProcesos();
            
        } catch (Exception e) {
            panelConsola.escribirError("✗ Error en paso a paso: " + e.getMessage());
            e.printStackTrace();
        }
    }    

   
    /**
     * Actualiza el panel de un CPU específico
     */
    private void actualizarPanelCPU(int cpuId) {
        javax.swing.JTextArea[] textAreas = {
            jTextArea_CPU1, jTextArea_CPU2, jTextArea_CPU3, jTextArea_CPU4, jTextArea_CPU5
        };
        
        if (cpuId < 0 || cpuId >= textAreas.length) {
            return;
        }
        
        javax.swing.JTextArea textArea = textAreas[cpuId];
        BCP bcp = sistemaOperativo.getBCPEnCPU(cpuId);
        
        if (bcp == null) {
            textArea.setText("Sin proceso en ejecución");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== PROCESO ACTUAL ===\n");
        sb.append("Nombre: ").append(bcp.getNombreProceso()).append("\n");
        sb.append("ID: ").append(bcp.getIdProceso()).append("\n");
        sb.append("Estado: ").append(bcp.getEstado()).append("\n");
        sb.append("PC: ").append(bcp.getPC()).append(" / ").append(bcp.getTamanoProceso());
        sb.append(String.format(" (%.1f%%)\n", bcp.getProgreso()));
        sb.append("\n=== REGISTROS ===\n");
        sb.append("AC: ").append(bcp.getAC()).append("\n");
        sb.append("AX: ").append(bcp.getAX()).append("\n");
        sb.append("BX: ").append(bcp.getBX()).append("\n");
        sb.append("CX: ").append(bcp.getCX()).append("\n");
        sb.append("DX: ").append(bcp.getDX()).append("\n");
        
        sb.append("\n=== INSTRUCCIÓN ACTUAL ===\n");
        if (bcp.getIR() != null) {
            sb.append(bcp.getIR().toString()).append("\n");
        } else {
            sb.append("Sin instrucción\n");
        }
        
        sb.append("\n=== MEMORIA ===\n");
        if (bcp.tieneParticionAsignada()) {
            sb.append("Partición: ").append(bcp.getIndiceParticion()).append("\n");
            sb.append("Tamaño: ").append(bcp.getTamanoParticion()).append(" KB\n");
            sb.append("Frag. Interna: ").append(bcp.getFragmentacionInterna()).append(" KB\n");
        } else if (bcp.tieneBloqueBuddyAsignado()) {
            sb.append("Bloque Buddy: ").append(bcp.getIndiceBloqueMemoria()).append("\n");
            sb.append("Nivel: ").append(bcp.getNivelBuddy()).append("\n");
            sb.append("Tamaño: ").append(bcp.getTamanoBloqueAsignado()).append(" KB\n");
            sb.append("Frag. Interna: ").append(bcp.getFragmentacionInterna()).append(" KB\n");
        }
        
        sb.append("\n=== TIEMPO ===\n");
        sb.append("CPU usado: ").append(bcp.getTiempoCPUUsado()).append(" ciclos\n");
        sb.append("Tiempo espera: ").append(bcp.getTiempoEspera()).append("\n");
        
        textArea.setText(sb.toString());
        textArea.setCaretPosition(0);
    }
    
    /**
     * Actualiza el estado de los botones según el estado del sistema
     */
    private void actualizarEstadoBotones() {
        boolean activo = sistemaOperativo.isSistemaActivo();
        boolean pausado = sistemaOperativo.isEjecucionPausada();
        
        if (activo) {
            jButton_ejecutarAutomatico.setText("Pausar");
            jButton_pasoApaso.setEnabled(false);
            jButton_iniciarPrograma.setEnabled(false);
        } else if (pausado) {
            jButton_ejecutarAutomatico.setText("Reanudar");
            jButton_pasoApaso.setEnabled(true);
            jButton_iniciarPrograma.setEnabled(true);
        } else {
            jButton_ejecutarAutomatico.setText("Ejecutar");
            jButton_pasoApaso.setEnabled(true);
            jButton_iniciarPrograma.setEnabled(true);
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButton_iniciarPrograma = new javax.swing.JButton();
        jButton_pasoApaso = new javax.swing.JButton();
        jButton_cargarArchivos = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable_memoriaPrincipal = new javax.swing.JTable();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable_memoriaSecundaria = new javax.swing.JTable();
        panelConsola = new so.gui.PanelConsola();
        jButton_ejecutarAutomatico = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        jTextArea_CPU1 = new javax.swing.JTextArea();
        jScrollPane4 = new javax.swing.JScrollPane();
        jTextArea_CPU2 = new javax.swing.JTextArea();
        jScrollPane5 = new javax.swing.JScrollPane();
        jTextArea_CPU3 = new javax.swing.JTextArea();
        jScrollPane6 = new javax.swing.JScrollPane();
        jTextArea_CPU4 = new javax.swing.JTextArea();
        jScrollPane7 = new javax.swing.JScrollPane();
        jTextArea_CPU5 = new javax.swing.JTextArea();
        jLabel_CPU1 = new javax.swing.JLabel();
        jLabel_CPU2 = new javax.swing.JLabel();
        jLabel_CPU3 = new javax.swing.JLabel();
        jLabel_CPU4 = new javax.swing.JLabel();
        jLabel_CPU5 = new javax.swing.JLabel();
        jScrollPane8 = new javax.swing.JScrollPane();
        jTable_infoProcesos = new javax.swing.JTable();
        jLabel3 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new java.awt.Dimension(1200, 600));

        jButton_iniciarPrograma.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton_iniciarPrograma.setText("Iniciar");

        jButton_pasoApaso.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton_pasoApaso.setText("Paso a paso");

        jButton_cargarArchivos.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton_cargarArchivos.setText("Cargar Archivos");
        jButton_cargarArchivos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_cargarArchivosActionPerformed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel1.setText("Memoria Secundaria");

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel2.setText("Memoria Principal");

        jTable_memoriaPrincipal.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String [] {
                "Posición", "Valor"
            }
        ));
        jScrollPane2.setViewportView(jTable_memoriaPrincipal);

        jTable_memoriaSecundaria.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Posición", "Valor"
            }
        ));
        jScrollPane1.setViewportView(jTable_memoriaSecundaria);

        jButton_ejecutarAutomatico.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton_ejecutarAutomatico.setText("Ejecutar");

        jTextArea_CPU1.setColumns(20);
        jTextArea_CPU1.setRows(5);
        jScrollPane3.setViewportView(jTextArea_CPU1);

        jTextArea_CPU2.setColumns(20);
        jTextArea_CPU2.setRows(5);
        jScrollPane4.setViewportView(jTextArea_CPU2);

        jTextArea_CPU3.setColumns(20);
        jTextArea_CPU3.setRows(5);
        jScrollPane5.setViewportView(jTextArea_CPU3);

        jTextArea_CPU4.setColumns(20);
        jTextArea_CPU4.setRows(5);
        jScrollPane6.setViewportView(jTextArea_CPU4);

        jTextArea_CPU5.setColumns(20);
        jTextArea_CPU5.setRows(5);
        jScrollPane7.setViewportView(jTextArea_CPU5);

        jLabel_CPU1.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_CPU1.setText("CPU 0 - BCP Actual");

        jLabel_CPU2.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_CPU2.setText("CPU 1 - BCP Actual");

        jLabel_CPU3.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_CPU3.setText("CPU 2 - BCP Actual");

        jLabel_CPU4.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_CPU4.setText("CPU 3 - BCP Actual");

        jLabel_CPU5.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_CPU5.setText("CPU 4 - BCP Actual");

        jTable_infoProcesos.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "Proceso", "Rafaga", "T. Llegada", "CPU", "Estado"
            }
        ));
        jScrollPane8.setViewportView(jTable_infoProcesos);

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel3.setText("Procesos");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jButton_cargarArchivos)
                        .addGap(18, 18, 18)
                        .addComponent(jButton_iniciarPrograma)
                        .addGap(18, 18, 18)
                        .addComponent(jButton_ejecutarAutomatico)
                        .addGap(18, 18, 18)
                        .addComponent(jButton_pasoApaso))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(12, 12, 12)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(panelConsola, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(54, 54, 54)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, 427, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jLabel3))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jLabel_CPU3)
                        .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(55, 55, 55)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel_CPU1)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel_CPU4))
                .addGap(39, 55, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel_CPU2)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel_CPU5)
                    .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(23, 23, 23))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jButton_cargarArchivos)
                            .addComponent(jButton_iniciarPrograma)
                            .addComponent(jButton_pasoApaso)
                            .addComponent(jButton_ejecutarAutomatico))
                        .addGap(28, 28, 28)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel1)
                            .addComponent(jLabel2)
                            .addComponent(jLabel3))
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 400, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 400, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(panelConsola, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(88, 88, 88)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel_CPU2)
                            .addComponent(jLabel_CPU1))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(2, 2, 2)
                                .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel_CPU5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel_CPU4)
                                    .addComponent(jLabel_CPU3))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE))))))
                .addContainerGap(36, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton_cargarArchivosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_cargarArchivosActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jButton_cargarArchivosActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new FrmMain().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton_cargarArchivos;
    private javax.swing.JButton jButton_ejecutarAutomatico;
    private javax.swing.JButton jButton_iniciarPrograma;
    private javax.swing.JButton jButton_pasoApaso;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel_CPU1;
    private javax.swing.JLabel jLabel_CPU2;
    private javax.swing.JLabel jLabel_CPU3;
    private javax.swing.JLabel jLabel_CPU4;
    private javax.swing.JLabel jLabel_CPU5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JTable jTable_infoProcesos;
    private javax.swing.JTable jTable_memoriaPrincipal;
    private javax.swing.JTable jTable_memoriaSecundaria;
    private javax.swing.JTextArea jTextArea_CPU1;
    private javax.swing.JTextArea jTextArea_CPU2;
    private javax.swing.JTextArea jTextArea_CPU3;
    private javax.swing.JTextArea jTextArea_CPU4;
    private javax.swing.JTextArea jTextArea_CPU5;
    private so.gui.PanelConsola panelConsola;
    // End of variables declaration//GEN-END:variables
}
