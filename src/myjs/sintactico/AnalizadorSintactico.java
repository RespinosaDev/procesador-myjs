package myjs.sintactico;

import myjs.errores.GestorErrores;
import myjs.lexico.AnalizadorLexico;
import myjs.lexico.Token;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analizador sintáctico descendente con tablas (LL(1) predictivo no recursivo).
 *
 * La pila contiene tres tipos de elementos:
 * <ul>
 *   <li><b>Terminal</b>: se empareja con el token de entrada.</li>
 *   <li><b>No terminal</b>: se sustituye por el consecuente de la regla M[X, a].</li>
 *   <li><b>Acción semántica</b>: se ejecuta al desapilarse (pila extendida).</li>
 * </ul>
 *
 * El siguiente token se pide al léxico solo cuando hace falta consultarlo (al emparejar un
 * terminal o al expandir un no terminal). Así las acciones se ejecutan antes de leer el token
 * siguiente, que es lo que permite activar y desactivar la zona de declaración a tiempo.
 */
public final class AnalizadorSintactico {

    /** Elemento de la pila: un símbolo (con su nodo) o una acción ligada a los nodos de su regla. */
    private static final class Elemento {
        final Nodo nodo;
        final Accion accion;
        final Nodo padre;
        final Nodo[] hijos;

        Elemento(Nodo nodo) {
            this.nodo = nodo;
            this.accion = null;
            this.padre = null;
            this.hijos = null;
        }

        Elemento(Accion accion, Nodo padre, Nodo[] hijos) {
            this.nodo = null;
            this.accion = accion;
            this.padre = padre;
            this.hijos = hijos;
        }
    }

    private final Gramatica gramatica;
    private final AnalizadorLexico lexico;
    private final GestorErrores errores;
    private final Map<String, Accion> acciones;

    private final List<Integer> parse = new ArrayList<>();
    private Token lookahead;

    public AnalizadorSintactico(Gramatica gramatica, AnalizadorLexico lexico, GestorErrores errores,
                                Map<String, Accion> acciones) {
        this.gramatica = gramatica;
        this.lexico = lexico;
        this.errores = errores;
        this.acciones = acciones;
    }

    private Token lookahead() {
        if (lookahead == null) {
            lookahead = lexico.siguienteToken();
        }
        return lookahead;
    }

    /**
     * Analiza el programa completo.
     *
     * @return true si el programa es sintácticamente correcto
     */
    public boolean analizar() {
        Deque<Elemento> pila = new ArrayDeque<>();
        pila.push(new Elemento(new Nodo(Gramatica.AXIOMA)));

        while (!pila.isEmpty()) {
            Elemento cima = pila.pop();

            if (cima.accion != null) {
                cima.accion.ejecutar(cima.padre, cima.hijos);
                continue;
            }

            Nodo nodo = cima.nodo;
            Token a = lookahead();

            if (!gramatica.esNoTerminal(nodo.simbolo)) {
                if (nodo.simbolo.equals(a.codigo.terminal)) {
                    nodo.token = a;
                    lookahead = null;  // consumido
                } else {
                    errores.sintactico(a.linea, "Se esperaba " + mostrar(nodo.simbolo)
                            + " pero se encontró " + a.describir());
                    terminarLectura();
                    return false;
                }
                continue;
            }

            Gramatica.Regla regla = gramatica.regla(nodo.simbolo, a.codigo.terminal);
            if (regla == null) {
                errores.sintactico(a.linea, "Se encontró " + a.describir() + " donde se esperaba "
                        + Descripciones.de(nodo.simbolo) + " (" + listaEsperados(gramatica.esperados(nodo.simbolo)) + ")");
                terminarLectura();
                return false;
            }
            parse.add(regla.numero);
            apilarConsecuente(pila, nodo, regla);
        }
        return true;
    }

    private void apilarConsecuente(Deque<Elemento> pila, Nodo padre, Gramatica.Regla regla) {
        Nodo[] hijos = new Nodo[regla.simbolos.size()];
        for (int i = 0; i < hijos.length; i++) {
            hijos[i] = new Nodo(regla.simbolos.get(i));
        }
        // Se apila de derecha a izquierda para que el primer símbolo quede en la cima.
        List<String> cons = regla.simbolosConAcciones;
        int h = hijos.length - 1;
        for (int i = cons.size() - 1; i >= 0; i--) {
            String s = cons.get(i);
            if (Gramatica.esAccion(s)) {
                Accion acc = acciones.get(Gramatica.nombreAccion(s));
                if (acc == null) {
                    throw new IllegalStateException("Acción semántica no definida: " + s);
                }
                pila.push(new Elemento(acc, padre, hijos));
            } else {
                pila.push(new Elemento(hijos[h--]));
            }
        }
    }

    /** Tras un error sintáctico se sigue leyendo para completar el fichero de tokens. */
    private void terminarLectura() {
        // Se lee el resto del fichero solo para listar sus tokens y errores léxicos:
        // ya no hay contexto de declaración fiable.
        lexico.setZonaDeclaracion(false);
        Token t = lookahead();
        while (t.codigo != Token.Codigo.EOF) {
            t = lexico.siguienteToken();
        }
    }

    public List<Integer> getParse() {
        return parse;
    }

    private static String mostrar(String terminal) {
        switch (terminal) {
            case "id": return "un identificador";
            case "entero": return "una constante entera";
            case "real": return "una constante real";
            case "cadena": return "una cadena";
            case "eof": return "el final del fichero";
            default: return "'" + terminal + "'";
        }
    }

    private static String listaEsperados(Set<String> esperados) {
        List<String> l = new ArrayList<>();
        for (String t : esperados) {
            l.add(mostrar(t));
        }
        return "se admite: " + String.join(", ", l);
    }
}
