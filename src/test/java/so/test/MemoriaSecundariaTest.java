package so.test;

import java.util.Arrays;
import java.util.List;
import so.memoria.MemoriaSecundaria;

/**
 *
 * @author dylan
 */
public class MemoriaSecundariaTest {
    public static void main(String[] args) {
        MemoriaSecundaria sm = new MemoriaSecundaria(90,60);

        // Simular programas en "disco" (no parseados aún)
        List<String> lineas1 = Arrays.asList(
            "PARAM 10, 5",
            "MOV AX, 0",
            "MOV BX, 0",
            "POP AX",
            "POP BX",
            "ADD BX",
            "MOV DX, AC",
            "INT 10H",
            "MOV AX, DX",
            "SUB BX",
            "MOV DX, AC",
            "INT 10H",
            "INT 20H"
        );

        List<String> lineas2 = Arrays.asList(
            "MOV AX, 0",
            "MOV BX, 10",
            "MOV CX, 0",
            "INT 09H",
            "MOV AX, DX",
            "MOV CX, AX",
            "LOAD AX",
            "ADD BX",
            "MOV DX, AC",
            "INT 10H",
            "INC CX",
            "MOV DX, CX",
            "INT 10H",
            "DEC CX",
            "MOV DX, CX",
            "INT 10H",
            "INT 20H"
        );

        List<String> lineas3 = Arrays.asList(
            "PARAM 15, 25",
            "MOV AX, 0",
            "MOV BX, 0",
            "POP AX",
            "POP BX",
            "MOV DX, AX",
            "INT 10H",
            "MOV DX, BX",
            "INT 10H",
            "SWAP AX, BX",
            "MOV DX, AX",
            "INT 10H",
            "MOV DX, BX",
            "INT 10H",
            "INT 20H"
        );

        // Preparar arrays para UnidadDeAlmacenamiento
        String[] nombres = {"Programa1", "Programa2", "Programa3"};
        List<String>[] programas = new List[]{lineas1, lineas2, lineas3};

        try {
            // Cargar programas (solo strings, como si fueran en disco)
            sm.cargarProgramas(nombres, programas);
        } catch (Exception e) {
            System.out.println(e);
        }        
        


        // Mostrar estado del almacenamiento
        sm.mostrarAlmacenamiento();
    }    
}
