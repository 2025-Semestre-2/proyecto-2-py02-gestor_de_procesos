package so.gui;

import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import so.main.SistemaOperativo;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.EstadoProceso;
import so.estadisticas.EstadisticasProceso;

/**
 * Interfaz gráfica mejorada con gestión automática de procesos pendientes
 * @author dylan
 */
public class Main extends javax.swing.JFrame {
    
    private final SistemaOperativo sistema = new SistemaOperativo();
    private final Map<Integer, Color> mapaColoresSecundaria = new HashMap<>();
    private final Map<Integer, Color> mapaColoresPrincipal = new HashMap<>();
    private final Map<Integer, Color> coloresPorID = new HashMap<>();
    private SwingWorker<Void, Void> workerEjecucion = null;
    
    private enum EstadoSistema {
        INICIAL,
        PROGRAMAS_CARGADOS,
        PROCESOS_LISTOS,
        EJECUTANDO,
        PAUSADO
    }
    
    private EstadoSistema estadoActual = EstadoSistema.INICIAL;
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Main.class.getName());   
    
    public Main() {
        initComponents();
        inicializarComponentes();
        configurarListeners();
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        actualizarEstadoBotones();
    }

    private void inicializarComponentes() {
        inicializarJTable(jTable_memoriaSecundaria);
        inicializarJTable(jTable_memoriaPrincipal);
        inicializarTablaEstados();
        jTextArea_BCPactual.setEditable(false);
    }
    
    /**
     * Configura los listeners del sistema operativo
     */
    private void configurarListeners() {
        sistema.setCargaProcesoListener(new SistemaOperativo.CargaProcesoListener() {
            @Override
            public void onProcesoCargado(String nombreProceso) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    panelConsola.escribir("✓ Proceso cargado: " + nombreProceso);
                    actualizarVisualizacion();
                });
            }

            @Override
            public void onProcesoFinalizado(String nombreProceso) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    panelConsola.escribir("✓ Proceso finalizado: " + nombreProceso);
                    
                    // Mostrar información de programas pendientes
                    if (sistema.hayProgramasPendientes()) {
                        panelConsola.escribir("⚠ " + sistema.getProgramasPendientes().size() + 
                            " programa(s) pendiente(s) en memoria secundaria");
                    }
                    
                    actualizarVisualizacion();
                });
            }
        });
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
    }

    private void inicializarTablaEstados() {
        DefaultTableModel modelo = new DefaultTableModel(
            new Object[][]{},
            new String[]{"Proceso", "Estado"}
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        jTable_ProcessStates.setModel(modelo);
    }

    private void actualizarEstadoBotones() {
        switch (estadoActual) {
            case INICIAL:
                jButton_cargarArchivos.setEnabled(true);
                jButton_iniciarPrograma.setText("Iniciar");
                jButton_iniciarPrograma.setEnabled(false);
                jButton_pasoApaso.setEnabled(false);
                break;
                
            case PROGRAMAS_CARGADOS:
                jButton_cargarArchivos.setEnabled(true);
                jButton_iniciarPrograma.setText("Iniciar");
                jButton_iniciarPrograma.setEnabled(true);
                jButton_pasoApaso.setEnabled(false);
                break;
                
            case PROCESOS_LISTOS:
            case PAUSADO:
                jButton_cargarArchivos.setEnabled(false);
                jButton_iniciarPrograma.setText("Ejecutar");
                jButton_iniciarPrograma.setEnabled(true);
                jButton_pasoApaso.setEnabled(true);
                break;
                
            case EJECUTANDO:
                jButton_cargarArchivos.setEnabled(false);
                jButton_iniciarPrograma.setText("Detener");
                jButton_iniciarPrograma.setEnabled(true);
                jButton_pasoApaso.setEnabled(false);
                break;
        }
    }

    public void cargarArchivos() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos ASM", "asm"));

        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File[] archivos = fileChooser.getSelectedFiles();
        if (archivos.length == 0) return;

        try {
            String[] nombres = new String[archivos.length];
            List<String>[] programas = new ArrayList[archivos.length];

            for (int i = 0; i < archivos.length; i++) {
                File archivo = archivos[i];
                nombres[i] = archivo.getName().replace(".asm", "");
                programas[i] = java.nio.file.Files.readAllLines(archivo.toPath());
            }

            boolean cargado = sistema.cargarProgramasAMemoriaSecundaria(nombres, programas);
            if (!cargado) {
                JOptionPane.showMessageDialog(this, "Error al cargar programas.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            actualizarTablaMemoriaSecundaria();
            estadoActual = EstadoSistema.PROGRAMAS_CARGADOS;
            actualizarEstadoBotones();
            
            panelConsola.escribir("✓ Se cargaron " + archivos.length + " programas en memoria secundaria.");
            
            if (sistema.hayProgramasPendientes()) {
                panelConsola.escribir("📋 Programas cargados: " + String.join(", ", sistema.getProgramasPendientes()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actualizarTablaMemoriaSecundaria() {
        Object[] contenido = sistema.getMemoriaSecundaria().getAlmacenamiento();
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

    private void actualizarTablaMemoriaPrincipal() {
        Object[] contenido = sistema.getMemoriaPrincipal().getMemoriaCompleta();
        DefaultTableModel modelo = (DefaultTableModel) jTable_memoriaPrincipal.getModel();
        modelo.setRowCount(0);
        mapaColoresPrincipal.clear();

        int direccionActual = -1;
        BCP procesoActual = sistema.getProcesoEnEjecucion();
        if (procesoActual != null) {
            direccionActual = procesoActual.getDireccionBase() + procesoActual.getPC();
        }

        List<BCP> procesosActivos = sistema.getBCPsActivos();
        if (coloresPorID.isEmpty() && !procesosActivos.isEmpty()) {
            Random random = new Random(42);
            for (BCP proceso : procesosActivos) {
                coloresPorID.put(proceso.getIdProceso(), new Color(180 + random.nextInt(75), 
                                                        180 + random.nextInt(75), 
                                                        180 + random.nextInt(75)));
            }
        }

        for (int i = 0; i < sistema.getMemoriaPrincipal().getMaxProcesos(); i++) {
            BCP bcp = sistema.getMemoriaPrincipal().obtenerBCP(i);
            if (bcp != null) {
                Color color = coloresPorID.get(bcp.getIdProceso());
                int indiceBCP = 10 + (i * 25);
                
                for (int j = 0; j < 25; j++) {
                    mapaColoresPrincipal.put(indiceBCP + j, color);
                }
                
                int base = bcp.getDireccionBase();
                int tamano = bcp.getTamanoProceso();
                for (int j = 0; j < tamano; j++) {
                    mapaColoresPrincipal.put(base + j, color);
                }
            }
        }

        String[] nombresCamposBCP = {
            "ID", "Nombre", "Estado", "PC", "DirecciónBase", "Tamaño",
            "AC", "AX", "BX", "CX", "DX", "IR", "StackPointer",
            "Pila[0]", "Pila[1]", "Pila[2]", "Pila[3]", "Pila[4]",
            "Prioridad", "TiempoInicio", "TiempoCPU", "FlagComparación",
            "TiempoEspera", "QuantumRestante", "ArchivosAbiertos"
        };

        for (int i = 0; i < contenido.length; i++) {
            Object celda = contenido[i];
            String valorMostrar;

            if (i >= 10 && i < 135) {
                int numeroBCP = (i - 10) / 25;
                int offset = (i - 10) % 25;
                BCP bcp = sistema.getMemoriaPrincipal().obtenerBCP(numeroBCP);
                
                if (bcp != null && offset < nombresCamposBCP.length) {
                    valorMostrar = String.format("BCP %d - %s: %s", 
                        numeroBCP, 
                        nombresCamposBCP[offset],
                        celda != null ? celda.toString() : "null");
                } else {
                    valorMostrar = celda != null ? celda.toString() : "null";
                }
            } else {
                valorMostrar = celda != null ? celda.toString() : "null";
            }

            modelo.addRow(new Object[]{i, valorMostrar});
        }

        final int direccionActualFinal = direccionActual;
        jTable_memoriaPrincipal.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (row == direccionActualFinal) {
                    c.setBackground(new Color(100, 150, 255));
                    c.setForeground(Color.WHITE);
                } else {
                    Color color = mapaColoresPrincipal.get(row);
                    c.setBackground(color != null ? color : Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });

        if (direccionActual >= 0 && direccionActual < modelo.getRowCount()) {
            jTable_memoriaPrincipal.scrollRectToVisible(
                jTable_memoriaPrincipal.getCellRect(direccionActual, 0, true)
            );
        }

        jTable_memoriaPrincipal.repaint();
    }

    private void actualizarTablaEstados() {
        DefaultTableModel modelo = (DefaultTableModel) jTable_ProcessStates.getModel();
        modelo.setRowCount(0);

        List<BCP> procesos = sistema.getBCPsActivos();
        for (BCP proceso : procesos) {
            modelo.addRow(new Object[]{proceso.getNombreProceso(), proceso.getEstado().toString()});
        }
        
        // Mostrar información de programas pendientes
        if (sistema.hayProgramasPendientes()) {
            List<String> pendientes = sistema.getProgramasPendientes();
            for (String nombre : pendientes) {
                modelo.addRow(new Object[]{"[Pendiente] " + nombre, "En Memoria Secundaria"});
            }
        }
    }

    private void actualizarBCPActual() {
        BCP procesoActual = sistema.getProcesoEnEjecucion();
        
        if (procesoActual == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("No hay proceso en ejecución\n\n");
            
            if (sistema.hayProgramasPendientes()) {
                sb.append("=== PROGRAMAS PENDIENTES ===\n");
                List<String> pendientes = sistema.getProgramasPendientes();
                for (int i = 0; i < pendientes.size(); i++) {
                    sb.append(String.format("%d. %s\n", i + 1, pendientes.get(i)));
                }
                sb.append("\nEsperando espacio en memoria...");
            }
            
            jTextArea_BCPactual.setText(sb.toString());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("========== BCP ACTUAL ==========\n");
        sb.append(String.format("ID: %d\n", procesoActual.getIdProceso()));
        sb.append(String.format("Nombre: %s\n", procesoActual.getNombreProceso()));
        sb.append(String.format("Estado: %s\n", procesoActual.getEstado()));
        sb.append(String.format("PC: %d / %d\n", procesoActual.getPC(), procesoActual.getTamanoProceso()));
        sb.append(String.format("Progreso: %.1f%%\n", 
            procesoActual.getTamanoProceso() > 0 ? 
            (double) procesoActual.getPC() / procesoActual.getTamanoProceso() * 100 : 0));
        sb.append("\nRegistros:\n");
        sb.append(String.format("  AC: %d\n", procesoActual.getAC()));
        sb.append(String.format("  AX: %d\n", procesoActual.getAX()));
        sb.append(String.format("  BX: %d\n", procesoActual.getBX()));
        sb.append(String.format("  CX: %d\n", procesoActual.getCX()));
        sb.append(String.format("  DX: %d\n", procesoActual.getDX()));
        sb.append(String.format("\nTiempo CPU: %d seg\n", procesoActual.getTiempoCPUUsado()));
        sb.append("================================");

        jTextArea_BCPactual.setText(sb.toString());
    }

    public void iniciar() {
        switch (estadoActual) {
            case PROGRAMAS_CARGADOS:
                cargarProcesosIniciales();
                break;
            case PROCESOS_LISTOS:
            case PAUSADO:
                ejecutarAutomatico();
                break;
            case EJECUTANDO:
                detenerEjecucion();
                break;
        }
    }

    /**
     * Carga los primeros 5 procesos (o menos si hay menos disponibles)
     */
    private void cargarProcesosIniciales() {
        if (!sistema.hayProgramasPendientes()) {
            JOptionPane.showMessageDialog(this, 
                "No hay programas en memoria secundaria.", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        int cargados = 0;
        int maxProcesos = sistema.getMemoriaPrincipal().getMaxProcesos();
        List<String> pendientes = sistema.getProgramasPendientes();
        
        for (int i = 0; i < Math.min(maxProcesos, pendientes.size()); i++) {
            String programa = pendientes.get(i);
            int id = sistema.cargarProcesoAMemoriaPrincipal(programa);
            if (id > 0) {
                cargados++;
            }
        }

        if (cargados > 0) {
            panelConsola.escribir("✓ Se cargaron " + cargados + " procesos en memoria principal.");
            
            if (sistema.hayProgramasPendientes()) {
                panelConsola.escribir("⚠ " + sistema.getProgramasPendientes().size() + 
                    " programa(s) quedaron en espera en memoria secundaria.");
                panelConsola.escribir("💡 Se cargarán automáticamente cuando se libere memoria.");
            }

            sistema.admitirTodosProcesos();
            actualizarVisualizacion();
            estadoActual = EstadoSistema.PROCESOS_LISTOS;
            actualizarEstadoBotones();
        } else {
            JOptionPane.showMessageDialog(this, 
                "No se pudieron cargar procesos.", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void ejecutarAutomatico() {
        if (workerEjecucion != null && !workerEjecucion.isDone()) {
            return;
        }

        if (!sistema.isSistemaActivo()) {
            if (!sistema.iniciarSistema()) {
                JOptionPane.showMessageDialog(this, 
                    "No se pudo iniciar el sistema.", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        estadoActual = EstadoSistema.EJECUTANDO;
        actualizarEstadoBotones();
        panelConsola.escribir("\n▶ Iniciando ejecución automática...\n");

        workerEjecucion = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                while (!isCancelled() && sistema.isSistemaActivo()) {
                    boolean resultado = sistema.ejecutarSiguienteInstruccion();
                    
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        actualizarVisualizacion();
                    });
                    
                    Thread.sleep(1000);

                    // Verificar si hay trabajo pendiente
                    if (sistema.getColaListos().length == 0 && sistema.getProcesoEnEjecucion() == null) {
                        if (!sistema.hayProgramasPendientes()) {
                            break; // Todo terminado
                        }
                        // Hay programas pendientes, esperar a que se carguen automáticamente
                        Thread.sleep(500);
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                estadoActual = EstadoSistema.PAUSADO;
                actualizarEstadoBotones();
                
                if (!isCancelled()) {
                    panelConsola.escribir("\n✓ Ejecución completada.");
                    mostrarEstadisticas();
                } else {
                    panelConsola.escribir("\n⏸ Ejecución detenida.");
                }
            }
        };

        workerEjecucion.execute();
    }

    private void detenerEjecucion() {
        if (workerEjecucion != null && !workerEjecucion.isDone()) {
            workerEjecucion.cancel(true);
        }
        sistema.detenerSistema();
        estadoActual = EstadoSistema.PAUSADO;
        actualizarEstadoBotones();
    }

    public void ejecutarPasoAPaso() {
        if (estadoActual == EstadoSistema.EJECUTANDO) {
            JOptionPane.showMessageDialog(this, 
                "Detenga la ejecución automática primero.", 
                "Advertencia", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (estadoActual != EstadoSistema.PROCESOS_LISTOS && 
            estadoActual != EstadoSistema.PAUSADO) {
            return;
        }

        if (!sistema.isSistemaActivo()) {
            if (!sistema.iniciarSistema()) {
                JOptionPane.showMessageDialog(this, 
                    "No se pudo iniciar el sistema.", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        boolean resultado = sistema.ejecutarSiguienteInstruccion();
        actualizarVisualizacion();
        
        if (resultado) {
            BCP procesoActual = sistema.getProcesoEnEjecucion();
            if (procesoActual != null) {
                panelConsola.escribir(String.format(
                    "→ [PC=%d] %s | AC=%d AX=%d BX=%d CX=%d DX=%d",
                    procesoActual.getPC(),
                    procesoActual.getIR() != null ? procesoActual.getIR().toString() : "N/A",
                    procesoActual.getAC(),
                    procesoActual.getAX(),
                    procesoActual.getBX(),
                    procesoActual.getCX(),
                    procesoActual.getDX()
                ));
            }
        } else {
            panelConsola.escribir("✗ No se pudo ejecutar la instrucción");
        }
        
        // Verificar si terminó la ejecución
        if (sistema.getProcesoEnEjecucion() == null && sistema.getColaListos().length == 0) {
            if (!sistema.hayProgramasPendientes()) {
                panelConsola.escribir("\n✓ Todos los procesos han terminado.");
                mostrarEstadisticas();
            }
        }
    }

    private void actualizarVisualizacion() {
        actualizarTablaMemoriaPrincipal();
        actualizarTablaEstados();
        actualizarBCPActual();
    }

    private void mostrarEstadisticas() {
        Map<Integer, EstadisticasProceso> statsMap = sistema.getTodasEstadisticas();
        List<EstadisticasProceso> stats = new ArrayList<>(statsMap.values());
        
        if (stats.isEmpty()) {
            return;
        }

        panelConsola.escribir("\n========== ESTADÍSTICAS ==========");
        panelConsola.escribir(String.format("%-20s | %8s | %8s | %10s | %8s",
            "Proceso", "Inicio", "Fin", "Duración", "Ráfaga"));
        panelConsola.escribir("=".repeat(75));
        
        for (EstadisticasProceso stat : stats) {
            panelConsola.escribir(stat.toString());
        }
        
        int duracionTotal = stats.stream()
            .mapToInt(EstadisticasProceso::getDuracionSegundos)
            .sum();
        
        panelConsola.escribir("=".repeat(75));
        panelConsola.escribir("Total de procesos: " + stats.size());
        panelConsola.escribir("Tiempo total: " + duracionTotal + " segundos");
        panelConsola.escribir("==================================\n");
    }
                                                 
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTable_memoriaSecundaria = new javax.swing.JTable();
        jButton_cargarArchivos = new javax.swing.JButton();
        jButton_Config = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTable_memoriaPrincipal = new javax.swing.JTable();
        jLabel3 = new javax.swing.JLabel();
        panelConsola = new so.gui.PanelConsola();
        jButton_iniciarPrograma = new javax.swing.JButton();
        jButton_pasoApaso = new javax.swing.JButton();
        jScrollPane6 = new javax.swing.JScrollPane();
        jTable_ProcessStates = new javax.swing.JTable();
        jLabel4 = new javax.swing.JLabel();
        jScrollPane4 = new javax.swing.JScrollPane();
        jTextArea_BCPactual = new javax.swing.JTextArea();
        jLabel5 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new java.awt.Dimension(1200, 600));

        jTable_memoriaSecundaria.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Posición", "Valor"
            }
        ));
        jScrollPane1.setViewportView(jTable_memoriaSecundaria);

        jButton_cargarArchivos.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton_cargarArchivos.setText("Cargar Archivos");
        jButton_cargarArchivos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_cargarArchivosActionPerformed(evt);
            }
        });

        jButton_Config.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton_Config.setText("Configuración");

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

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel3.setText("Terminal");

        jButton_iniciarPrograma.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton_iniciarPrograma.setText("Iniciar");
        jButton_iniciarPrograma.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_iniciarProgramaActionPerformed(evt);
            }
        });

        jButton_pasoApaso.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton_pasoApaso.setText("Paso a paso");
        jButton_pasoApaso.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton_pasoApasoActionPerformed(evt);
            }
        });

        jTable_ProcessStates.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String [] {
                "Procesos", "Estados"
            }
        ));
        jScrollPane6.setViewportView(jTable_ProcessStates);

        jLabel4.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel4.setText("BCP Actual");

        jTextArea_BCPactual.setColumns(20);
        jTextArea_BCPactual.setRows(5);
        jScrollPane4.setViewportView(jTextArea_BCPactual);

        jLabel5.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel5.setText("Procesos Actuales");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 297, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel5))
                        .addGap(30, 30, 30)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(jLabel4)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 274, Short.MAX_VALUE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(jScrollPane4)
                                        .addGap(68, 68, 68)))
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel3)
                                    .addComponent(panelConsola, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jButton_cargarArchivos)
                        .addGap(18, 18, 18)
                        .addComponent(jButton_iniciarPrograma)
                        .addGap(18, 18, 18)
                        .addComponent(jButton_pasoApaso)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButton_Config)))
                .addGap(30, 30, 30))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton_Config)
                    .addComponent(jButton_cargarArchivos)
                    .addComponent(jButton_iniciarPrograma)
                    .addComponent(jButton_pasoApaso))
                .addGap(10, 10, 10)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addGap(10, 10, 10)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 230, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4)
                            .addComponent(jLabel5))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(panelConsola, javax.swing.GroupLayout.DEFAULT_SIZE, 230, Short.MAX_VALUE)
                            .addComponent(jScrollPane4))))
                .addContainerGap(29, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton_cargarArchivosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_cargarArchivosActionPerformed
        cargarArchivos();
    }//GEN-LAST:event_jButton_cargarArchivosActionPerformed

    private void jButton_iniciarProgramaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_iniciarProgramaActionPerformed
        iniciar();
    }//GEN-LAST:event_jButton_iniciarProgramaActionPerformed

    private void jButton_pasoApasoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton_pasoApasoActionPerformed
        ejecutarPasoAPaso();
    }//GEN-LAST:event_jButton_pasoApasoActionPerformed

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
        java.awt.EventQueue.invokeLater(() -> new Main().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton_Config;
    private javax.swing.JButton jButton_cargarArchivos;
    private javax.swing.JButton jButton_iniciarPrograma;
    private javax.swing.JButton jButton_pasoApaso;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane6;
    public javax.swing.JTable jTable_ProcessStates;
    private javax.swing.JTable jTable_memoriaPrincipal;
    private javax.swing.JTable jTable_memoriaSecundaria;
    private javax.swing.JTextArea jTextArea_BCPactual;
    private so.gui.PanelConsola panelConsola;
    // End of variables declaration//GEN-END:variables
}
