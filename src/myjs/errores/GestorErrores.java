package myjs.errores;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Recoge los errores de todas las fases del procesador. Cada error indica la fase
 * (léxico, sintáctico o semántico), la línea del fuente y un mensaje para el usuario.
 */
public class GestorErrores {

    public enum Tipo {
        LEXICO("léxico"), SINTACTICO("sintáctico"), SEMANTICO("semántico");

        private final String nombre;

        Tipo(String nombre) {
            this.nombre = nombre;
        }
    }

    public static final class Error {
        public final Tipo tipo;
        public final int linea;
        public final String mensaje;

        Error(Tipo tipo, int linea, String mensaje) {
            this.tipo = tipo;
            this.linea = linea;
            this.mensaje = mensaje;
        }

        @Override
        public String toString() {
            return "Error " + tipo.nombre + " (línea " + linea + "): " + mensaje;
        }
    }

    private final List<Error> errores = new ArrayList<>();

    public void lexico(int linea, String mensaje) {
        errores.add(new Error(Tipo.LEXICO, linea, mensaje));
    }

    public void sintactico(int linea, String mensaje) {
        errores.add(new Error(Tipo.SINTACTICO, linea, mensaje));
    }

    public void semantico(int linea, String mensaje) {
        errores.add(new Error(Tipo.SEMANTICO, linea, mensaje));
    }

    public boolean hayErrores() {
        return !errores.isEmpty();
    }

    public List<Error> getErrores() {
        return Collections.unmodifiableList(errores);
    }

    public void volcar(Path fichero) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(fichero, StandardCharsets.UTF_8))) {
            for (Error e : errores) {
                out.println(e);
            }
        }
    }
}
