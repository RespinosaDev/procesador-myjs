package myjs;

import myjs.errores.GestorErrores;
import myjs.lexico.AnalizadorLexico;
import myjs.semantico.AnalizadorSemantico;
import myjs.sintactico.AnalizadorSintactico;
import myjs.sintactico.Gramatica;
import myjs.ts.GestorTS;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Procesador del lenguaje MyJS (PdL-JS): análisis léxico, sintáctico y semántico.
 *
 * <pre>
 * Uso: java -jar myjs.jar &lt;programa.javascript&gt; [directorio_salida]
 *      java -jar myjs.jar --gramatica [directorio_salida]
 * </pre>
 *
 * Genera en el directorio de salida (por defecto, {@code salida/}):
 * tokens.txt, parse.txt, ts.txt y errores.txt.
 * Con {@code --gramatica} genera gramatica.txt (para VASt) y tablaLL1.txt.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Uso: java -jar myjs.jar <programa.javascript> [directorio_salida]");
            System.err.println("     java -jar myjs.jar --gramatica [directorio_salida]");
            System.exit(2);
        }
        if (args[0].equals("--gramatica")) {
            Path dir = Paths.get(args.length > 1 ? args[1] : ".");
            escribirGramatica(dir);
            System.out.println("Gramática escrita en " + dir.resolve("gramatica.txt"));
            return;
        }

        Path fuente = Paths.get(args[0]);
        Path dir = Paths.get(args.length > 1 ? args[1] : "salida");
        Resultado r = procesar(fuente, dir);

        if (r.errores.hayErrores()) {
            System.out.println("Se encontraron " + r.errores.getErrores().size() + " error(es):");
            r.errores.getErrores().forEach(e -> System.out.println("  " + e));
        } else {
            System.out.println("Programa correcto.");
        }
        System.out.println("Ficheros generados en " + dir.toAbsolutePath());
        System.exit(r.errores.hayErrores() ? 1 : 0);
    }

    /** Resultado de procesar un programa (lo usan también los tests). */
    public static final class Resultado {
        public final GestorErrores errores;
        public final List<Integer> parse;
        public final boolean sintaxisCorrecta;

        Resultado(GestorErrores errores, List<Integer> parse, boolean sintaxisCorrecta) {
            this.errores = errores;
            this.parse = parse;
            this.sintaxisCorrecta = sintaxisCorrecta;
        }
    }

    public static Resultado procesar(Path fuente, Path dirSalida) throws IOException {
        Files.createDirectories(dirSalida);
        String programa = new String(Files.readAllBytes(fuente), StandardCharsets.UTF_8);

        GestorErrores errores = new GestorErrores();
        Gramatica gramatica = new Gramatica();

        try (PrintWriter tokens = writer(dirSalida.resolve("tokens.txt"));
             PrintWriter tablas = writer(dirSalida.resolve("ts.txt"))) {

            GestorTS ts = new GestorTS(tablas);
            AnalizadorLexico lexico = new AnalizadorLexico(programa, tokens, errores, ts);
            AnalizadorSemantico semantico = new AnalizadorSemantico(ts, errores, lexico);
            AnalizadorSintactico sintactico = new AnalizadorSintactico(gramatica, lexico, errores, semantico.acciones());

            boolean ok = sintactico.analizar();
            ts.destruirTablaGlobal();

            try (PrintWriter parse = writer(dirSalida.resolve("parse.txt"))) {
                parse.println("Descendente " + sintactico.getParse().stream()
                        .map(String::valueOf).collect(Collectors.joining(" ")));
            }
            errores.volcar(dirSalida.resolve("errores.txt"));
            return new Resultado(errores, sintactico.getParse(), ok);
        }
    }

    public static void escribirGramatica(Path dir) throws IOException {
        Files.createDirectories(dir);
        Gramatica g = new Gramatica();
        try (PrintWriter out = writer(dir.resolve("gramatica.txt"))) {
            g.escribirVASt(out);
        }
        try (PrintWriter out = writer(dir.resolve("tablaLL1.txt"))) {
            g.escribirTablaLL1(out);
        }
    }

    private static PrintWriter writer(Path p) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8));
    }
}
