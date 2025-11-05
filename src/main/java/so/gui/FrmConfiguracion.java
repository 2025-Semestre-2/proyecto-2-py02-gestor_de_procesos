package so.gui;

import utils.NumericDocumentFilter;

/**
 *
 * @author dylan
 */
public class FrmConfiguracion extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FrmConfiguracion.class.getName());

    /**
     * Creates new form FrmConfiguracion
     */
    public FrmConfiguracion() {
        initComponents();
        configurarComboCantidadCPUs();
        aplicarFiltrosNumericos();
        configurarCheckBoxes();
        configurarValidaciones();
        configurarComboTipoFijo();
        this.setLocationRelativeTo(null);
    }


    private void configurarComboCantidadCPUs() {
        // Primero ocultamos todo
        mostrarCPUs(1);

        // Listener que detecta los cambios en el combo
        jComboBox_cantidadCPUs.addActionListener(e -> {
            int cantidad = Integer.parseInt((String) jComboBox_cantidadCPUs.getSelectedItem());
            mostrarCPUs(cantidad);
        });
    }

    private void mostrarCPUs(int cantidad) {
        // Todos los labels y combos en arreglos para recorrerlos
        javax.swing.JLabel[] labels = {
            jLabel_cpu1, jLabel_cpu2, jLabel_cpu3, jLabel_cpu4, jLabel_cpu5
        };
        javax.swing.JComboBox<?>[] combos = {
            jComboBox_planificadorCPU1, jComboBox_planificadorCPU2, jComboBox_planificadorCPU3,
            jComboBox_planificadorCPU4, jComboBox_planificadorCPU5
        };

        // Mostrar/ocultar según la cantidad seleccionada
        for (int i = 0; i < 5; i++) {
            boolean visible = i < cantidad;
            labels[i].setVisible(visible);
            combos[i].setVisible(visible);
        }

        // Refrescar el layout para que se actualice visualmente
        this.revalidate();
        this.repaint();
    }    

    private void configurarComboTipoFijo() {
        jComboBox_tipoFijo.addActionListener(e -> {
            String seleccion = (String) jComboBox_tipoFijo.getSelectedItem();
            if ("Igual".equalsIgnoreCase(seleccion)) {
                jTextField_tamanoParticionFija.setEnabled(true);
            } else {
                jTextField_tamanoParticionFija.setEnabled(false);
                jTextField_tamanoParticionFija.setText("");
            }
        });
    }    
    
    private void configurarCheckBoxes() {
        javax.swing.JCheckBox[] allBoxes = {
            jCheckBox_particionamientoFijo,
            jCheckBox_particionamientoDinamico,
            jCheckBox_paginacion,
            jCheckBox_segmentacion
        };

        jCheckBox_particionamientoFijo.addActionListener(e -> manejarSeleccion(jCheckBox_particionamientoFijo, allBoxes, false, false));
        jCheckBox_particionamientoDinamico.addActionListener(e -> manejarSeleccion(jCheckBox_particionamientoDinamico, allBoxes, true, true));
        jCheckBox_paginacion.addActionListener(e -> manejarSeleccion(jCheckBox_paginacion, allBoxes, true, true));
        jCheckBox_segmentacion.addActionListener(e -> manejarSeleccion(jCheckBox_segmentacion, allBoxes, true, true));
    }

    private void manejarSeleccion(javax.swing.JCheckBox seleccionado, javax.swing.JCheckBox[] todos, boolean deshabilitarTamano, boolean deshabilitarComboTipoFijo) {
        boolean activo = seleccionado.isSelected();

        for (javax.swing.JCheckBox box : todos) {
            if (box != seleccionado) {
                box.setEnabled(!activo);
            }
        }

        jTextField_tamanoParticionFija.setEnabled(!deshabilitarTamano && activo);
        jTextField_tamanoParticionFija.setEnabled(!deshabilitarComboTipoFijo && activo);
    }

    private void configurarValidaciones() {
        jButton_Config.addActionListener(e -> {
            try {
                int memoriaVirtual = Integer.parseInt(jTextField_totalMemoriaVirtual.getText());
                int memoriaSecundaria = Integer.parseInt(jTextField_totalMemoriaSecundaria.getText());
                int tamanoUsuario = Integer.parseInt(jTextField_totalUsuario.getText());
                int particionFija = jTextField_tamanoParticionFija.isEnabled() && !jTextField_tamanoParticionFija.getText().isEmpty()
                        ? Integer.parseInt(jTextField_tamanoParticionFija.getText()) : 0;

                if (memoriaVirtual > memoriaSecundaria) {
                    javax.swing.JOptionPane.showMessageDialog(this,
                            "El tamaño de la memoria virtual no puede ser mayor que la memoria secundaria.",
                            "Error de configuración",
                            javax.swing.JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (particionFija > tamanoUsuario) {
                    javax.swing.JOptionPane.showMessageDialog(this,
                            "El tamaño de la partición fija no puede ser mayor que la memoria del usuario.",
                            "Error de configuración",
                            javax.swing.JOptionPane.ERROR_MESSAGE);
                    return;
                }

                javax.swing.JOptionPane.showMessageDialog(this,
                        "Configuración válida.",
                        "Éxito",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);

            } catch (NumberFormatException ex) {
                javax.swing.JOptionPane.showMessageDialog(this,
                        "Todos los campos deben contener números válidos.",
                        "Error de formato",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        });
    }    
    
    private void aplicarFiltrosNumericos() {
        javax.swing.text.AbstractDocument doc1 = (javax.swing.text.AbstractDocument) jTextField_totalMemoriaVirtual.getDocument();
        javax.swing.text.AbstractDocument doc2 = (javax.swing.text.AbstractDocument) jTextField_totalUsuario.getDocument();
        javax.swing.text.AbstractDocument doc3 = (javax.swing.text.AbstractDocument) jTextField_totalMemoriaSecundaria.getDocument();
        javax.swing.text.AbstractDocument doc4 = (javax.swing.text.AbstractDocument) jTextField_tamanoParticionFija.getDocument();

        doc1.setDocumentFilter(new NumericDocumentFilter());
        doc2.setDocumentFilter(new NumericDocumentFilter());
        doc3.setDocumentFilter(new NumericDocumentFilter());
        doc4.setDocumentFilter(new NumericDocumentFilter());
    }    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel_titulo = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jTextField_totalMemoriaVirtual = new javax.swing.JTextField();
        jTextField_totalMemoriaSecundaria = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jComboBox_estrategiasMemoriaSecundaria = new javax.swing.JComboBox<>();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jTextField_totalUsuario = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jComboBox_cantidadCPUs = new javax.swing.JComboBox<>();
        jLabel_cpu1 = new javax.swing.JLabel();
        jComboBox_planificadorCPU1 = new javax.swing.JComboBox<>();
        jLabel_cpu2 = new javax.swing.JLabel();
        jComboBox_planificadorCPU2 = new javax.swing.JComboBox<>();
        jLabel_cpu3 = new javax.swing.JLabel();
        jComboBox_planificadorCPU3 = new javax.swing.JComboBox<>();
        jLabel_cpu4 = new javax.swing.JLabel();
        jComboBox_planificadorCPU4 = new javax.swing.JComboBox<>();
        jComboBox_planificadorCPU5 = new javax.swing.JComboBox<>();
        jLabel_cpu5 = new javax.swing.JLabel();
        jCheckBox_paginacion = new javax.swing.JCheckBox();
        jCheckBox_segmentacion = new javax.swing.JCheckBox();
        jCheckBox_particionamientoFijo = new javax.swing.JCheckBox();
        jCheckBox_particionamientoDinamico = new javax.swing.JCheckBox();
        jLabel12 = new javax.swing.JLabel();
        jComboBox_tipoFijo = new javax.swing.JComboBox<>();
        jLabel13 = new javax.swing.JLabel();
        jTextField_tamanoParticionFija = new javax.swing.JTextField();
        jButton_Config = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel_titulo.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel_titulo.setText("Configuración");

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel2.setText("Tamaño total:");

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel3.setText("Tamaño para virtual:");

        jLabel4.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel4.setText("Memoria Secundaria");

        jLabel5.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel5.setText("Estrategia:");

        jComboBox_estrategiasMemoriaSecundaria.setEditable(true);
        jComboBox_estrategiasMemoriaSecundaria.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Paginación", "Particionamiento Fijo", "Particionamiento Dinámico" }));

        jLabel6.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel6.setText("Tamaño del SO: 1000");

        jLabel7.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel7.setText("Tamaño para el usuario:");

        jTextField_totalUsuario.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField_totalUsuarioActionPerformed(evt);
            }
        });

        jLabel8.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel8.setText("Memoria Principal");

        jLabel9.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel9.setText("Estrategia");

        jLabel10.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel10.setText("CPUs");

        jLabel11.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel11.setText("Cantidad:");

        jComboBox_cantidadCPUs.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "1", "2", "3", "4", "5" }));

        jLabel_cpu1.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_cpu1.setText("CPU 1:");

        jComboBox_planificadorCPU1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "FCFS", "SRT", "SJF", "RR", "HRRN" }));

        jLabel_cpu2.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_cpu2.setText("CPU 2:");

        jComboBox_planificadorCPU2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "FCFS", "SRT", "SJF", "RR", "HRRN" }));

        jLabel_cpu3.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_cpu3.setText("CPU 3:");

        jComboBox_planificadorCPU3.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "FCFS", "SRT", "SJF", "RR", "HRRN" }));

        jLabel_cpu4.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_cpu4.setText("CPU 4:");

        jComboBox_planificadorCPU4.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "FCFS", "SRT", "SJF", "RR", "HRRN" }));

        jComboBox_planificadorCPU5.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "FCFS", "SRT", "SJF", "RR", "HRRN" }));

        jLabel_cpu5.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel_cpu5.setText("CPU 5:");

        jCheckBox_paginacion.setText("Paginación");

        jCheckBox_segmentacion.setText("Segmentación");

        jCheckBox_particionamientoFijo.setText("Particionamiento Fijo");

        jCheckBox_particionamientoDinamico.setText("Particionamiento Dinamico");

        jLabel12.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel12.setText("Tipo de Particionamiento:");

        jComboBox_tipoFijo.setEditable(true);
        jComboBox_tipoFijo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Igual", "Desigual" }));

        jLabel13.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel13.setText("Tamaño de partición:");

        jButton_Config.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jButton_Config.setText("Configurar");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(34, 34, 34)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTextField_totalMemoriaSecundaria, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel10)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(11, 11, 11)
                        .addComponent(jLabel11)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBox_cantidadCPUs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel8)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel9)
                            .addComponent(jLabel6)))
                    .addComponent(jLabel4)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(42, 42, 42)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel_cpu1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBox_planificadorCPU1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel_cpu2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBox_planificadorCPU2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel_cpu3)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jComboBox_planificadorCPU3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(297, 297, 297)
                                .addComponent(jButton_Config)))
                        .addGap(18, 18, 18)
                        .addComponent(jLabel_cpu4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBox_planificadorCPU4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel_cpu5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBox_planificadorCPU5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(76, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel_titulo)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(56, 56, 56)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jCheckBox_particionamientoDinamico)
                            .addComponent(jCheckBox_segmentacion)
                            .addComponent(jCheckBox_paginacion)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jCheckBox_particionamientoFijo)
                                .addGap(55, 55, 55)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(jLabel7)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jTextField_totalUsuario, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(jLabel12)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jComboBox_tipoFijo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(jLabel3)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jTextField_totalMemoriaVirtual, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                        .addGap(18, 18, 18)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(jLabel5)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jComboBox_estrategiasMemoriaSecundaria, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(jLabel13)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jTextField_tamanoParticionFija, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)))))))))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel_titulo)
                .addGap(18, 18, 18)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jTextField_totalMemoriaSecundaria, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(jTextField_totalMemoriaVirtual, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5)
                    .addComponent(jComboBox_estrategiasMemoriaSecundaria, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 94, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel6))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(39, 39, 39)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel7)
                            .addComponent(jTextField_totalUsuario, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addGap(18, 18, 18)
                .addComponent(jLabel9)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jCheckBox_paginacion)
                .addGap(18, 18, 18)
                .addComponent(jCheckBox_segmentacion)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jCheckBox_particionamientoFijo)
                    .addComponent(jLabel12)
                    .addComponent(jComboBox_tipoFijo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel13)
                    .addComponent(jTextField_tamanoParticionFija, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jCheckBox_particionamientoDinamico)
                .addGap(39, 39, 39)
                .addComponent(jLabel10)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(jComboBox_cantidadCPUs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel_cpu1)
                    .addComponent(jComboBox_planificadorCPU1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel_cpu2)
                    .addComponent(jComboBox_planificadorCPU2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel_cpu3)
                    .addComponent(jComboBox_planificadorCPU3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel_cpu4)
                    .addComponent(jComboBox_planificadorCPU4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel_cpu5)
                    .addComponent(jComboBox_planificadorCPU5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(44, 44, 44)
                .addComponent(jButton_Config)
                .addGap(24, 24, 24))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jTextField_totalUsuarioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField_totalUsuarioActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField_totalUsuarioActionPerformed

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
        java.awt.EventQueue.invokeLater(() -> new FrmConfiguracion().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton_Config;
    private javax.swing.JCheckBox jCheckBox_paginacion;
    private javax.swing.JCheckBox jCheckBox_particionamientoDinamico;
    private javax.swing.JCheckBox jCheckBox_particionamientoFijo;
    private javax.swing.JCheckBox jCheckBox_segmentacion;
    private javax.swing.JComboBox<String> jComboBox_cantidadCPUs;
    private javax.swing.JComboBox<String> jComboBox_estrategiasMemoriaSecundaria;
    private javax.swing.JComboBox<String> jComboBox_planificadorCPU1;
    private javax.swing.JComboBox<String> jComboBox_planificadorCPU2;
    private javax.swing.JComboBox<String> jComboBox_planificadorCPU3;
    private javax.swing.JComboBox<String> jComboBox_planificadorCPU4;
    private javax.swing.JComboBox<String> jComboBox_planificadorCPU5;
    private javax.swing.JComboBox<String> jComboBox_tipoFijo;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JLabel jLabel_cpu1;
    private javax.swing.JLabel jLabel_cpu2;
    private javax.swing.JLabel jLabel_cpu3;
    private javax.swing.JLabel jLabel_cpu4;
    private javax.swing.JLabel jLabel_cpu5;
    private javax.swing.JLabel jLabel_titulo;
    private javax.swing.JTextField jTextField_tamanoParticionFija;
    private javax.swing.JTextField jTextField_totalMemoriaSecundaria;
    private javax.swing.JTextField jTextField_totalMemoriaVirtual;
    private javax.swing.JTextField jTextField_totalUsuario;
    // End of variables declaration//GEN-END:variables
}
