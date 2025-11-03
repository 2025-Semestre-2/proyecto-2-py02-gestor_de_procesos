package so.instrucciones;

import so.cpu.CPU;
import so.memoria.MemoriaPrincipal;
import so.gestordeprocesos.Despachador;
import so.gestordeprocesos.BCP;
import so.gestordeprocesos.EstadoProceso;
import java.util.ArrayList;
import java.util.List;

/**
 * Ejecutor de instrucciones del sistema operativo.
 * Coordina el CPU para ejecutar instrucciones de procesos.
 * 
 * Responsabilidades:
 * - Coordinar el ciclo fetch-decode-execute usando el CPU
 * - Ejecutar la siguiente instrucción del proceso en ejecución
 * - Actualizar el estado del proceso (PC, registros, pila, etc.)
 * - Manejar interrupciones (INT 10H, INT 20H)
 * - Detectar errores en tiempo de ejecución
 * 
 * @author dylan
 */
public class EjecutorInstrucciones {
    
    private final CPU cpu;
    private final MemoriaPrincipal memoria;
    private final Despachador despachador;
    private final List<String> pantalla; // buffer de salida para INT 10H
    
    /**
     * Constructor del ejecutor
     * 
     * @param memoria referencia a la memoria principal
     * @param despachador referencia al despachador
     */
    public EjecutorInstrucciones(MemoriaPrincipal memoria, Despachador despachador) {
        if (memoria == null || despachador == null) {
            throw new IllegalArgumentException("Memoria y despachador no pueden ser nulos");
        }
        this.cpu = new CPU();
        this.memoria = memoria;
        this.despachador = despachador;
        this.pantalla = new ArrayList<>();
    }
    
    /**
     * Ejecuta la siguiente instrucción del proceso actual
     * Implementa el ciclo completo: Fetch -> Decode -> Execute
     * 
     * @return true si se ejecutó correctamente, false si el proceso terminó
     */
    public boolean ejecutarSiguiente() {
        int numeroBCP = memoria.getBCPEnEjecucion();
        
        if (numeroBCP < 0) {
            return false; // no hay proceso en ejecución
        }
        
        BCP bcp = memoria.obtenerBCP(numeroBCP);
        
        if (bcp == null) {
            return false;
        }
        
        // Verificar si ya terminó todas las instrucciones
        if (bcp.getPC() >= bcp.getTamanoProceso()) {
            bcp.setEstado(EstadoProceso.FINALIZADO);
            memoria.actualizarBCP(numeroBCP, bcp);
            cpu.guardarContexto(bcp);
            despachador.detener();
            return false;
        }
        
        try {
            // ========== FASE 1: FETCH ==========
            // Cargar contexto del proceso al CPU
            cpu.cargarContexto(bcp);
            
            // Obtener la instrucción actual
            int direccion = bcp.getDireccionBase() + bcp.getPC();
            Instruccion instruccion = memoria.obtenerInstruccion(direccion);
            
            if (instruccion == null) {
                throw new RuntimeException("Error: instrucción no encontrada en dirección " + direccion);
            }
            
            // Guardar en IR del BCP para el fetch
            bcp.setIR(instruccion);
            
            // Simular fetch en el CPU
            cpu.fetch(bcp);
            
            // ========== FASE 2: DECODE ==========
            boolean valida = cpu.decode();
            
            if (!valida) {
                throw new RuntimeException("Error: instrucción inválida");
            }
            
            // ========== FASE 3: EXECUTE ==========
            boolean finalizado = ejecutarInstruccion(bcp, instruccion);
            
            // Guardar contexto del CPU de vuelta al BCP
            cpu.guardarContexto(bcp);
            
            if (!finalizado) {
                // Incrementar PC y tiempo de CPU
                bcp.setPC(bcp.getPC() + 1);
                bcp.incrementarTiempoCPU();
                cpu.incrementarInstrucciones();
            }
            
            // Incrementar ciclos según peso de la instrucción
            int peso = instruccion.getCodigoOperacion().getPeso();
            if (peso > 0) {
                cpu.incrementarCiclos(peso);
            }
            
            // Actualizar BCP en memoria
            memoria.actualizarBCP(numeroBCP, bcp);
            
            return !finalizado;
            
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
            cpu.guardarContexto(bcp);
            bcp.setEstado(EstadoProceso.FINALIZADO);
            memoria.actualizarBCP(numeroBCP, bcp);
            despachador.detener();
            return false;
        }
    }
    
    /**
     * Ejecuta una instrucción específica usando el CPU
     * 
     * @param bcp contexto del proceso
     * @param inst instrucción a ejecutar
     * @return true si el proceso finalizó, false si continúa
     */
    private boolean ejecutarInstruccion(BCP bcp, Instruccion inst) {
        
        CodigoOperacion op = inst.getCodigoOperacion();
        List<String> operandos = inst.getOperandos();
        
        switch (op) {
            case LOAD -> ejecutarLOAD(bcp, operandos);
            case STORE -> ejecutarSTORE(bcp, operandos);
            case MOV -> ejecutarMOV(bcp, operandos);
            case ADD -> ejecutarADD(bcp, operandos);
            case SUB -> ejecutarSUB(bcp, operandos);
            case INC -> ejecutarINC(bcp, operandos);
            case DEC -> ejecutarDEC(bcp, operandos);
            case SWAP -> ejecutarSWAP(bcp, operandos);
            case PUSH -> ejecutarPUSH(bcp, operandos);
            case POP -> ejecutarPOP(bcp, operandos);
            case JMP -> ejecutarJMP(bcp, operandos);
            case CMP -> ejecutarCMP(bcp, operandos);
            case JE -> ejecutarJE(bcp, operandos);
            case JNE -> ejecutarJNE(bcp, operandos);
            case PARAM -> ejecutarPARAM(bcp, operandos);
            case INT -> {
                return ejecutarINT(bcp, operandos);
            }
        }
        
        return false; // continúa ejecutando
    }
    
    // ========== IMPLEMENTACIÓN DE INSTRUCCIONES ==========
    
    private void ejecutarLOAD(BCP bcp, List<String> ops) {
        // LOAD reg: carga el valor del registro al AC usando CPU
        String reg = ops.get(0);
        int valor = cpu.obtenerRegistro(reg);
        cpu.setAC(valor);
    }
    
    private void ejecutarSTORE(BCP bcp, List<String> ops) {
        // STORE reg: almacena el valor del AC en el registro usando CPU
        String reg = ops.get(0);
        cpu.establecerRegistro(reg, cpu.getAC());
    }
    
    private void ejecutarMOV(BCP bcp, List<String> ops) {
        // MOV destino, origen
        String destino = ops.get(0);
        String origen = ops.get(1);
        
        int valor;
        if (esNumero(origen)) {
            valor = Integer.parseInt(origen);
        } else {
            valor = cpu.obtenerRegistro(origen);
        }
        
        cpu.establecerRegistro(destino, valor);
    }
    
    private void ejecutarADD(BCP bcp, List<String> ops) {
        // ADD reg: suma el valor del registro al AC usando CPU
        String reg = ops.get(0);
        int resultado = cpu.sumar("AC", reg);
        cpu.setAC(resultado);
    }
    
    private void ejecutarSUB(BCP bcp, List<String> ops) {
        // SUB reg: resta el valor del registro al AC usando CPU
        String reg = ops.get(0);
        int resultado = cpu.restar("AC", reg);
        cpu.setAC(resultado);
    }
    
    private void ejecutarINC(BCP bcp, List<String> ops) {
        if (ops.isEmpty()) {
            // INC: incrementa AC
            cpu.incrementarAC();
        } else {
            // INC reg: incrementa registro
            String reg = ops.get(0);
            cpu.incrementar(reg);
        }
    }
    
    private void ejecutarDEC(BCP bcp, List<String> ops) {
        if (ops.isEmpty()) {
            // DEC: decrementa AC
            cpu.decrementarAC();
        } else {
            // DEC reg: decrementa registro
            String reg = ops.get(0);
            cpu.decrementar(reg);
        }
    }
    
    private void ejecutarSWAP(BCP bcp, List<String> ops) {
        // SWAP reg1, reg2: intercambia valores usando CPU
        String reg1 = ops.get(0);
        String reg2 = ops.get(1);
        
        int valor1 = cpu.obtenerRegistro(reg1);
        int valor2 = cpu.obtenerRegistro(reg2);
        
        cpu.establecerRegistro(reg1, valor2);
        cpu.establecerRegistro(reg2, valor1);
    }
    
    private void ejecutarPUSH(BCP bcp, List<String> ops) {
        // PUSH reg: guarda el valor del registro en la pila
        String reg = ops.get(0);
        int valor = cpu.obtenerRegistro(reg);
        bcp.push(valor);
    }
    
    private void ejecutarPOP(BCP bcp, List<String> ops) {
        // POP reg: saca valor de la pila y lo guarda en el registro
        String reg = ops.get(0);
        int valor = bcp.pop();
        cpu.establecerRegistro(reg, valor);
    }
    
    private void ejecutarJMP(BCP bcp, List<String> ops) {
        // JMP [+/-n]: salta a la instrucción según desplazamiento
        int desplazamiento = Integer.parseInt(ops.get(0));
        int nuevoPC = cpu.getPC() + desplazamiento;
        
        // Validar que el nuevo PC esté dentro del rango válido
        if (nuevoPC < 0 || nuevoPC >= bcp.getTamanoProceso()) {
            throw new RuntimeException("Salto fuera de rango: PC=" + nuevoPC);
        }
        
        // Ajustar PC (se restará 1 porque después se incrementa automáticamente)
        cpu.setPC(nuevoPC - 1);
    }
    
    private void ejecutarCMP(BCP bcp, List<String> ops) {
        // CMP reg1, reg2: compara reg1 con reg2 usando CPU
        String reg1 = ops.get(0);
        String reg2 = ops.get(1);
        
        cpu.comparar(reg1, reg2);
    }
    
    private void ejecutarJE(BCP bcp, List<String> ops) {
        // JE [+/-n]: salta si la última comparación fue igual (flag == 0)
        if (cpu.getFlagComparacion() == 0) {
            int desplazamiento = Integer.parseInt(ops.get(0));
            int nuevoPC = cpu.getPC() + desplazamiento;
            
            if (nuevoPC < 0 || nuevoPC >= bcp.getTamanoProceso()) {
                throw new RuntimeException("Salto condicional fuera de rango: PC=" + nuevoPC);
            }
            
            cpu.setPC(nuevoPC - 1);
        }
    }
    
    private void ejecutarJNE(BCP bcp, List<String> ops) {
        // JNE [+/-n]: salta si la última comparación fue diferente (flag != 0)
        if (cpu.getFlagComparacion() != 0) {
            int desplazamiento = Integer.parseInt(ops.get(0));
            int nuevoPC = cpu.getPC() + desplazamiento;
            
            if (nuevoPC < 0 || nuevoPC >= bcp.getTamanoProceso()) {
                throw new RuntimeException("Salto condicional fuera de rango: PC=" + nuevoPC);
            }
            
            cpu.setPC(nuevoPC - 1);
        }
    }
    
    private void ejecutarPARAM(BCP bcp, List<String> ops) {
        // PARAM v1, v2, ..., vN: guarda parámetros en la pila
        for (String param : ops) {
            int valor = Integer.parseInt(param);
            bcp.push(valor);
        }
    }
    
    private boolean ejecutarINT(BCP bcp, List<String> ops) {
        // INT código: ejecuta interrupción
        String codigo = ops.get(0).toUpperCase();
        
        switch (codigo) {
            case "20H" -> {
                // INT 20H: Finalizar programa
                bcp.setEstado(EstadoProceso.FINALIZADO);
                despachador.detener();
                pantalla.add("[" + bcp.getNombreProceso() + "] Programa finalizado");
                System.out.println("[INT 20H] Proceso " + bcp.getNombreProceso() + " finalizado");
                return true; // indica que finalizó
            }
            case "10H" -> {
                // INT 10H: Imprimir valor de DX en pantalla
                int valor = cpu.getDX();
                String mensaje = "[" + bcp.getNombreProceso() + "] " + valor;
                pantalla.add(mensaje);
                System.out.println("[INT 10H] Salida: " + mensaje);
                return false; // continúa ejecutando
            }
            default -> throw new IllegalStateException("Interrupción no implementada: " + codigo);
        }
    }
    
    // ========== UTILIDADES ==========
    
    /**
     * Verifica si una cadena es un número
     */
    private boolean esNumero(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Obtiene el contenido de la pantalla (salidas de INT 10H)
     * 
     * @return lista de mensajes mostrados en pantalla
     */
    public List<String> getPantalla() {
        return new ArrayList<>(pantalla);
    }
    
    /**
     * Limpia el buffer de la pantalla
     */
    public void limpiarPantalla() {
        pantalla.clear();
    }
    
    /**
     * Obtiene la última línea mostrada en pantalla
     * 
     * @return última línea o null si está vacía
     */
    public String getUltimaLineaPantalla() {
        if (pantalla.isEmpty()) {
            return null;
        }
        return pantalla.get(pantalla.size() - 1);
    }
    
    /**
     * Obtiene el CPU del sistema
     * 
     * @return referencia al CPU
     */
    public CPU getCPU() {
        return cpu;
    }
    
    /**
     * Genera un reporte del estado del CPU
     * 
     * @return reporte del CPU
     */
    public String generarReporteCPU() {
        return cpu.generarReporte();
    }
    
    /**
     * Obtiene las estadísticas del CPU
     * 
     * @return string con el estado del CPU
     */
    public String getEstadoCPU() {
        return cpu.getEstado();
    }
}