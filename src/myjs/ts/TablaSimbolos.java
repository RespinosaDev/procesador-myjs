package myjs.ts;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Una tabla de símbolos (la global o la local de una función). */
public final class TablaSimbolos {
    public final int numero;
    public final String titulo;

    private final List<Entrada> entradas = new ArrayList<>();
    private final Map<String, Entrada> porLexema = new HashMap<>();
    private int desplazamiento = 0;

    TablaSimbolos(int numero, String titulo) {
        this.numero = numero;
        this.titulo = titulo;
    }

    public Entrada buscar(String lexema) {
        return porLexema.get(lexema);
    }

    Entrada insertar(String lexema) {
        Entrada e = new Entrada(lexema, entradas.size(), this);
        entradas.add(e);
        porLexema.put(lexema, e);
        return e;
    }

    /** Asigna tipo a una variable o parámetro y le reserva espacio en la zona de datos. */
    public void asignarTipoVariable(Entrada e, Tipo tipo) {
        e.tipo = tipo;
        e.despl = desplazamiento;
        desplazamiento += tipo.tamano;
    }

    /** Volcado en el formato del fichero de TS de la asignatura. */
    void volcar(PrintWriter out) {
        out.println(titulo + " #" + numero + ":");
        for (Entrada e : entradas) {
            out.println("* LEXEMA : '" + e.lexema + "'");
            if (e.tipo == null) {
                out.println("  (identificador usado sin declarar)");
                continue;
            }
            out.println("  ATRIBUTOS :");
            out.println("  + tipo : '" + e.tipo.nombre + "'");
            if (e.esFuncion()) {
                out.println("  + numParam : " + e.tiposParam.size());
                for (int i = 0; i < e.tiposParam.size(); i++) {
                    out.printf("  + TipoParam%02d : '%s'%n", i + 1, e.tiposParam.get(i).nombre);
                }
                out.println("  + TipoRetorno : '" + e.tipoRetorno.nombre + "'");
                out.println("  + EtiqFuncion : '" + e.etiqueta + "'");
            } else {
                out.println("  + despl : " + e.despl);
            }
            out.println("  --------------------");
        }
        out.println();
    }
}
