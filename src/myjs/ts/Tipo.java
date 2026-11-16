package myjs.ts;

/** Tipos del lenguaje, con el nombre que se vuelca en la TS y su tamaño en bytes. */
public enum Tipo {
    ENTERO("entero", 2),
    REAL("real", 4),
    LOGICO("logico", 1),
    CADENA("cadena", 64),
    VACIO("vacio", 0),
    FUNCION("funcion", 0),
    /** Tipo de una expresión con errores: evita mensajes en cascada. */
    ERROR("error", 0);

    public final String nombre;
    public final int tamano;

    Tipo(String nombre, int tamano) {
        this.nombre = nombre;
        this.tamano = tamano;
    }

    /** Nombre del tipo en el código fuente, para los mensajes de error. */
    public String enFuente() {
        switch (this) {
            case ENTERO: return "int";
            case REAL: return "float";
            case LOGICO: return "boolean";
            case CADENA: return "string";
            case VACIO: return "void";
            case FUNCION: return "función";
            default: return "erróneo";
        }
    }
}
