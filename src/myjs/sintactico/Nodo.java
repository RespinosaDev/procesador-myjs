package myjs.sintactico;

import myjs.lexico.Token;
import myjs.ts.Tipo;

import java.util.List;

/**
 * Registro de atributos de un símbolo en la pila del analizador.
 * Cada terminal o no terminal apilado tiene su nodo, y las acciones semánticas
 * leen y escriben sus atributos.
 */
public final class Nodo {
    public final String simbolo;

    /** Token reconocido (solo terminales, tras emparejarse). */
    public Token token;

    // --- atributos semánticos ---
    /** Tipo sintetizado. */
    public Tipo tipo;
    /** Atributo heredado: tipo de la parte izquierda, identificador, lista de argumentos... */
    public Object heredado;
    /** Lista de tipos (argumentos de una llamada). */
    public List<Tipo> lista;

    Nodo(String simbolo) {
        this.simbolo = simbolo;
    }
}
