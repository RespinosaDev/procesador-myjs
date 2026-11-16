package myjs.ts;

import java.io.PrintWriter;

/**
 * Gestiona los ámbitos: una tabla global y, mientras se analiza una función, su tabla local.
 * Las funciones no se pueden anidar, así que como mucho hay dos tablas vivas.
 *
 * Cada tabla se vuelca al fichero de TS justo antes de destruirse: las locales al terminar
 * su función y la global al terminar el programa.
 */
public final class GestorTS {
    private final PrintWriter salida;
    private final TablaSimbolos global;
    private TablaSimbolos local;
    private int contadorTablas = 1;

    public GestorTS(PrintWriter salida) {
        this.salida = salida;
        this.global = new TablaSimbolos(contadorTablas++, "TABLA PRINCIPAL");
    }

    public TablaSimbolos global() {
        return global;
    }

    public TablaSimbolos actual() {
        return local != null ? local : global;
    }

    public boolean enFuncion() {
        return local != null;
    }

    /** Busca primero en el ámbito local y después en el global. */
    public Entrada buscar(String lexema) {
        if (local != null) {
            Entrada e = local.buscar(lexema);
            if (e != null) {
                return e;
            }
        }
        return global.buscar(lexema);
    }

    public Entrada insertarEnActual(String lexema) {
        return actual().insertar(lexema);
    }

    public Entrada insertarEnGlobal(String lexema) {
        return global.insertar(lexema);
    }

    public void crearTablaLocal(String funcion) {
        local = new TablaSimbolos(contadorTablas++, "TABLA DE LA FUNCION " + funcion);
    }

    public void destruirTablaLocal() {
        if (local != null) {
            local.volcar(salida);
            local = null;
        }
    }

    public void destruirTablaGlobal() {
        destruirTablaLocal();
        global.volcar(salida);
        salida.flush();
    }
}
