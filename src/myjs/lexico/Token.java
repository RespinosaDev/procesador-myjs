package myjs.lexico;

/**
 * Token generado por el analizador léxico.
 *
 * El código es el nombre del token (LET, ID, NUM...). El atributo depende del token:
 * posición en la tabla de símbolos para ID, valor para las constantes y nada para el resto.
 */
public final class Token {

    public enum Codigo {
        // palabras reservadas
        BOOLEAN("boolean"), FLOAT("float"), FUNCTION("function"), IF("if"), INT("int"), LET("let"),
        READ("read"), RETURN("return"), STRING("string"), VOID("void"), WHILE("while"), WRITE("write"),
        // identificadores y constantes
        ID("id"), NUM("entero"), NUMREAL("real"), CADENA("cadena"),
        // operadores y signos
        IGUAL("="), DIVIGUAL("/="), DOBLEIGUAL("=="), MENOR("<"), MAS("+"), DIV("/"), NEGACION("!"),
        PARIZQ("("), PARDER(")"), LLAVEIZQ("{"), LLAVEDER("}"), COMA(","), PUNTOYCOMA(";"),
        EOF("eof");

        /** Nombre del terminal en la gramática (y en el fichero de VASt). */
        public final String terminal;

        Codigo(String terminal) {
            this.terminal = terminal;
        }
    }

    public final Codigo codigo;
    /** Lexema tal como aparece en el fuente (para mensajes de error). */
    public final String lexema;
    /** Atributo del token: posición en la TS, valor de la constante o null. */
    public final Object atributo;
    public final int linea;
    /** Solo para ID: entrada de la TS (null si era una redeclaración no válida). */
    public final myjs.ts.Entrada entrada;

    public Token(Codigo codigo, String lexema, Object atributo, int linea) {
        this(codigo, lexema, atributo, linea, null);
    }

    public Token(Codigo codigo, String lexema, Object atributo, int linea, myjs.ts.Entrada entrada) {
        this.codigo = codigo;
        this.lexema = lexema;
        this.atributo = atributo;
        this.linea = linea;
        this.entrada = entrada;
    }

    /** Formato del fichero de tokens: {@code <CODIGO, atributo>}. */
    public String formato() {
        String attr;
        if (atributo == null) {
            attr = "";
        } else if (codigo == Codigo.CADENA) {
            attr = "\"" + atributo + "\"";
        } else if (atributo instanceof java.math.BigDecimal) {
            attr = ((java.math.BigDecimal) atributo).toPlainString();
        } else {
            attr = atributo.toString();
        }
        return "<" + codigo.name() + "," + attr + ">";
    }

    /** Descripción para los mensajes de error. */
    public String describir() {
        switch (codigo) {
            case EOF:
                return "el final del fichero";
            case ID:
                return "el identificador '" + lexema + "'";
            case NUM:
            case NUMREAL:
                return "la constante " + lexema;
            case CADENA:
                return "la cadena \"" + lexema + "\"";
            default:
                return "'" + lexema + "'";
        }
    }

    @Override
    public String toString() {
        return formato();
    }
}
