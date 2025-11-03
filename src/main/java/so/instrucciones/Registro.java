package so.instrucciones;

/**
 *
 * @author dylan
 */
public enum Registro {
    AX, 
    BX, 
    CX, 
    DX, 
    AC;

    public static Registro fromString(String s) {
        s = s.trim().toUpperCase();
        switch (s) {
            case "AX" -> {
                return AX;
            }
            case "BX" -> {
                return BX;
            }
            case "CX" -> {
                return CX;
            }
            case "DX" -> {
                return DX;
            }
            case "AC" -> {
                return AC;
            }
            default ->
                throw new IllegalArgumentException("Registro desconocido: " + s);
        }
    }    
}
