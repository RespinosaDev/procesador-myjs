package myjs.sintactico;

/** Descripción de cada no terminal en términos del usuario, para los mensajes de error. */
final class Descripciones {
    private Descripciones() {
    }

    static String de(String noTerminal) {
        switch (noTerminal) {
            case "P": return "una sentencia, una declaración o una función";
            case "B": case "C": return "una sentencia o una declaración";
            case "S": return "una sentencia (asignación, llamada, write, read o return)";
            case "S2": return "una asignación ('=' o '/=') o una llamada a función";
            case "T": return "un tipo (int, float, boolean o string)";
            case "H": return "el tipo de retorno de la función (un tipo o void)";
            case "A": return "la lista de parámetros (o void)";
            case "K": return "otro parámetro o el cierre ')'";
            case "I": return "una inicialización o ';'";
            case "X": return "la expresión a devolver o ';'";
            case "L": return "los argumentos de la llamada";
            case "Q": return "otro argumento o el cierre ')'";
            case "E": case "R": case "U": case "V": return "una expresión";
            case "E2": case "R2": case "U2": case "V2": return "un operador o el final de la expresión";
            case "F": return "una función";
            default: return noTerminal;
        }
    }
}
