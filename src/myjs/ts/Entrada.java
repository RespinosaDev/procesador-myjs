package myjs.ts;

import java.util.ArrayList;
import java.util.List;

/** Entrada de la tabla de símbolos: una variable, un parámetro o una función. */
public final class Entrada {
    public final String lexema;
    /** Posición en su tabla (es el atributo del token ID). */
    public final int posicion;
    /** Tabla a la que pertenece. */
    public final TablaSimbolos tabla;

    /** null mientras no se conoce (identificador usado sin declarar todavía no clasificado). */
    public Tipo tipo;
    public int despl = -1;

    // --- solo funciones ---
    public final List<Tipo> tiposParam = new ArrayList<>();
    public Tipo tipoRetorno;
    public String etiqueta;

    /** Variable global creada implícitamente al usarla sin declarar. */
    public boolean implicita;

    Entrada(String lexema, int posicion, TablaSimbolos tabla) {
        this.lexema = lexema;
        this.posicion = posicion;
        this.tabla = tabla;
    }

    public boolean esFuncion() {
        return tipo == Tipo.FUNCION;
    }
}
