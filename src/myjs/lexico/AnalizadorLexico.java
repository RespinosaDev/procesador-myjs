package myjs.lexico;

import myjs.errores.GestorErrores;
import myjs.lexico.Token.Codigo;
import myjs.ts.Entrada;
import myjs.ts.GestorTS;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Analizador léxico de MyJS. Implementa el autómata finito determinista de la memoria:
 * cada llamada a {@link #siguienteToken()} lee caracteres hasta reconocer un token,
 * lo escribe en el fichero de tokens y lo devuelve al analizador sintáctico.
 *
 * Los identificadores se introducen en la tabla de símbolos aquí mismo:
 * <ul>
 *   <li>En <b>zona de declaración</b> (activada por el analizador semántico al leer
 *       {@code let}, la cabecera de una función o sus parámetros) se insertan en la tabla
 *       actual; si ya existían en ese ámbito es un error de redeclaración.</li>
 *   <li>Fuera de ella se buscan en la tabla local y después en la global. Si no aparecen,
 *       se crean en la global: en MyJS una variable no declarada es una global entera.</li>
 * </ul>
 */
public class AnalizadorLexico {

    private static final int MAX_ENTERO = 32767;
    private static final BigDecimal MAX_REAL = new BigDecimal("117549436.0");
    private static final int MAX_CADENA = 64;

    private static final Map<String, Codigo> RESERVADAS = new HashMap<>();

    static {
        for (Codigo c : new Codigo[]{Codigo.BOOLEAN, Codigo.FLOAT, Codigo.FUNCTION, Codigo.IF, Codigo.INT,
                Codigo.LET, Codigo.READ, Codigo.RETURN, Codigo.STRING, Codigo.VOID, Codigo.WHILE, Codigo.WRITE}) {
            RESERVADAS.put(c.terminal, c);
        }
    }

    private final String fuente;
    private final PrintWriter ficheroTokens;
    private final GestorErrores errores;
    private final GestorTS ts;

    private int pos = 0;
    private int linea = 1;
    private boolean zonaDeclaracion = false;

    public AnalizadorLexico(String fuente, PrintWriter ficheroTokens, GestorErrores errores, GestorTS ts) {
        this.fuente = fuente;
        this.ficheroTokens = ficheroTokens;
        this.errores = errores;
        this.ts = ts;
    }

    public void setZonaDeclaracion(boolean activa) {
        this.zonaDeclaracion = activa;
    }

    public Token siguienteToken() {
        Token t = reconocer();
        ficheroTokens.println(t.formato());
        return t;
    }

    // ------------------------------------------------------------------
    // Autómata
    // ------------------------------------------------------------------

    private boolean hayMas() {
        return pos < fuente.length();
    }

    private char actual() {
        return fuente.charAt(pos);
    }

    private boolean siguienteEs(char c) {
        return pos + 1 < fuente.length() && fuente.charAt(pos + 1) == c;
    }

    private Token reconocer() {
        while (true) {
            // q0: delimitadores
            while (hayMas() && Character.isWhitespace(actual())) {
                if (actual() == '\n') {
                    linea++;
                }
                pos++;
            }
            if (!hayMas()) {
                return new Token(Codigo.EOF, "eof", null, linea);
            }

            char c = actual();

            if (esLetra(c)) {
                return identificadorOReservada();
            }
            if (esDigito(c)) {
                Token t = numero();
                if (t != null) {
                    return t;
                }
                continue;
            }

            switch (c) {
                case '"': {
                    Token t = cadena();
                    if (t != null) {
                        return t;
                    }
                    continue;
                }
                case '/':
                    if (siguienteEs('/')) {           // q4 -> comentario de línea
                        while (hayMas() && actual() != '\n') {
                            pos++;
                        }
                        continue;
                    }
                    if (siguienteEs('=')) {
                        pos += 2;
                        return simple(Codigo.DIVIGUAL, "/=");
                    }
                    pos++;
                    return simple(Codigo.DIV, "/");
                case '=':
                    if (siguienteEs('=')) {
                        pos += 2;
                        return simple(Codigo.DOBLEIGUAL, "==");
                    }
                    pos++;
                    return simple(Codigo.IGUAL, "=");
                case '+': pos++; return simple(Codigo.MAS, "+");
                case '!': pos++; return simple(Codigo.NEGACION, "!");
                case '<': pos++; return simple(Codigo.MENOR, "<");
                case ',': pos++; return simple(Codigo.COMA, ",");
                case ';': pos++; return simple(Codigo.PUNTOYCOMA, ";");
                case '(': pos++; return simple(Codigo.PARIZQ, "(");
                case ')': pos++; return simple(Codigo.PARDER, ")");
                case '{': pos++; return simple(Codigo.LLAVEIZQ, "{");
                case '}': pos++; return simple(Codigo.LLAVEDER, "}");
                default:
                    errores.lexico(linea, "El carácter '" + c + "' no pertenece al lenguaje");
                    pos++;
            }
        }
    }

    private Token simple(Codigo codigo, String lexema) {
        return new Token(codigo, lexema, null, linea);
    }

    private static boolean esLetra(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean esDigito(char c) {
        return c >= '0' && c <= '9';
    }

    /** q1: letra (letra | dígito | _)* */
    private Token identificadorOReservada() {
        int inicio = pos;
        while (hayMas() && (esLetra(actual()) || esDigito(actual()) || actual() == '_')) {
            pos++;
        }
        String lexema = fuente.substring(inicio, pos);

        Codigo reservada = RESERVADAS.get(lexema);
        if (reservada != null) {
            return new Token(reservada, lexema, null, linea);
        }

        Entrada entrada;
        int posicion;
        if (zonaDeclaracion) {
            Entrada previa = ts.actual().buscar(lexema);
            if (previa != null) {
                errores.semantico(linea, "El identificador '" + lexema + "' ya está declarado en este ámbito");
                entrada = null;
                posicion = previa.posicion;
            } else {
                entrada = ts.insertarEnActual(lexema);
                posicion = entrada.posicion;
            }
        } else {
            entrada = ts.buscar(lexema);
            if (entrada == null) {
                entrada = ts.insertarEnGlobal(lexema);
                entrada.implicita = true;
            }
            posicion = entrada.posicion;
        }
        return new Token(Codigo.ID, lexema, posicion, linea, entrada);
    }

    /** q2/q18: d+ (. d+)? */
    private Token numero() {
        int inicio = pos;
        while (hayMas() && esDigito(actual())) {
            pos++;
        }
        if (hayMas() && actual() == '.') {
            pos++;
            if (!hayMas() || !esDigito(actual())) {
                errores.lexico(linea, "La constante real '" + fuente.substring(inicio, pos)
                        + "' debe tener al menos una cifra decimal");
                return null;
            }
            while (hayMas() && esDigito(actual())) {
                pos++;
            }
            String lexema = fuente.substring(inicio, pos);
            BigDecimal valor = new BigDecimal(lexema);
            if (valor.compareTo(MAX_REAL) > 0) {
                errores.lexico(linea, "La constante real " + lexema + " supera el máximo permitido (" + MAX_REAL + ")");
            }
            return new Token(Codigo.NUMREAL, lexema, valor, linea);
        }
        String lexema = fuente.substring(inicio, pos);
        int valor;
        try {
            valor = Integer.parseInt(lexema);
        } catch (NumberFormatException e) {
            valor = Integer.MAX_VALUE;
        }
        if (valor > MAX_ENTERO) {
            errores.lexico(linea, "La constante entera " + lexema + " supera el máximo permitido (" + MAX_ENTERO + ")");
            valor = MAX_ENTERO;
        }
        return new Token(Codigo.NUM, lexema, valor, linea);
    }

    /** q3: " c* " — sin saltos de línea y con 64 caracteres como máximo. */
    private Token cadena() {
        int lineaInicio = linea;
        pos++; // comilla de apertura
        StringBuilder sb = new StringBuilder();
        while (hayMas() && actual() != '"' && actual() != '\n') {
            sb.append(actual());
            pos++;
        }
        if (!hayMas() || actual() == '\n') {
            errores.lexico(lineaInicio, "Cadena sin cerrar: falta la comilla doble final");
            return null;
        }
        pos++; // comilla de cierre
        String texto = sb.toString();
        if (texto.length() > MAX_CADENA) {
            errores.lexico(lineaInicio, "La cadena tiene " + texto.length() + " caracteres y el máximo es " + MAX_CADENA);
        }
        return new Token(Codigo.CADENA, texto, texto, lineaInicio);
    }
}
