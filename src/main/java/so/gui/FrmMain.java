package so.gui;

import java.awt.Color;
import java.awt.Component;
import javax.swing.table.DefaultTableModel;
import so.main.SistemaOperativoV2;
import so.memoria.estrategias.EstrategiaParticionamientoFijo;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import so.gestordeprocesos.BCP;
import so.instrucciones.Instruccion;

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

        this.setSize(1400, 800);
        this.setResizable(false);        
        this.setLocationRelativeTo(null);
    }

    private void crearSistemaPrueba() {
        try {
            // Configuración de prueba
            int tamanoMemSecundaria = 164;
            int tamanoMemVirtual = 64;
            int tamanoMemUsuario = 1000;
            int cantidadCPUs = 2;
            
            // Estrategia de memoria (Particionamiento Fijo con particiones de 100 KB)
            String estrategiaMemoria = "DINAMICO";
            Object configEstrategia = 50;
            
            
            // Planificadores (FIFO para CPU 0, SJF para CPU 1)
            IPlanificador[] planificadores = new IPlanificador[] {
                new PlanificadorFIFO(),
                new PlanificadorSJF()
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
        configurarBotones();
    }    

    
    private void configurarBotones() {
        // Botón Cargar Archivos
        jButton_cargarArchivos.addActionListener(e -> cargarArchivosAction());       
        
        // Botón Iniciar (cargar a memoria principal)
        jButton_iniciarPrograma.addActionListener(e -> cargarMemoriaPrincipalAction());
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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(panelConsola, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel1))
                        .addGap(57, 57, 57)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(870, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton_cargarArchivos)
                    .addComponent(jButton_iniciarPrograma)
                    .addComponent(jButton_pasoApaso)
                    .addComponent(jButton_ejecutarAutomatico))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2))
                .addGap(10, 10, 10)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addComponent(panelConsola, javax.swing.GroupLayout.DEFAULT_SIZE, 290, Short.MAX_VALUE)
                .addContainerGap())
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
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable jTable_memoriaPrincipal;
    private javax.swing.JTable jTable_memoriaSecundaria;
    private so.gui.PanelConsola panelConsola;
    // End of variables declaration//GEN-END:variables
}
