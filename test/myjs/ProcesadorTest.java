package myjs;

import myjs.errores.GestorErrores;
import myjs.lexico.Token;
import myjs.sintactico.Gramatica;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ProcesadorTest {

    private static final Path PRUEBAS = Paths.get("pruebas");

    @TempDir
    Path salida;

    private Main.Resultado procesar(String caso) throws IOException {
        return Main.procesar(PRUEBAS.resolve(caso + ".javascript"), salida);
    }

    private Main.Resultado procesarTexto(String programa) throws IOException {
        Path f = salida.resolve("programa.javascript");
        Files.write(f, programa.getBytes(StandardCharsets.UTF_8));
        return Main.procesar(f, salida);
    }

    private static List<String> mensajes(Main.Resultado r) {
        return r.errores.getErrores().stream().map(Object::toString).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Gramática
    // ------------------------------------------------------------------

    @Test
    void laGramaticaEsLL1() {
        Gramatica g = new Gramatica();   // la construcción de la tabla falla si hay conflictos
        assertEquals(53, g.getReglas().size());
    }

    // ------------------------------------------------------------------
    // Casos correctos
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"correcto1", "correcto2", "correcto3", "correcto4", "correcto5"})
    void losCasosCorrectosNoTienenErrores(String caso) throws IOException {
        Main.Resultado r = procesar(caso);
        assertTrue(r.sintaxisCorrecta);
        assertEquals(List.of(), mensajes(r));
    }

    /**
     * Comprueba el parse igual que VASt: aplicando las reglas como derivación por la izquierda
     * desde el axioma se tiene que obtener exactamente la secuencia de tokens del fichero.
     */
    @ParameterizedTest
    @ValueSource(strings = {"correcto1", "correcto2", "correcto3", "correcto4", "correcto5"})
    void elParseDerivaExactamenteLosTokens(String caso) throws IOException {
        procesar(caso);
        Gramatica g = new Gramatica();

        String parse = new String(Files.readAllBytes(salida.resolve("parse.txt")), StandardCharsets.UTF_8).trim();
        String[] partes = parse.split("\\s+");
        assertEquals("Descendente", partes[0]);

        List<String> derivados = new ArrayList<>();
        Deque<String> pendientes = new ArrayDeque<>();
        pendientes.push(Gramatica.AXIOMA);
        for (int i = 1; i < partes.length; i++) {
            Gramatica.Regla regla = g.getReglas().get(Integer.parseInt(partes[i]) - 1);
            // los terminales que quedan delante del no terminal más a la izquierda ya están derivados
            while (!g.esNoTerminal(pendientes.peek())) {
                derivados.add(pendientes.pop());
            }
            assertEquals(regla.antecedente, pendientes.pop(), "regla " + regla.numero + " mal aplicada");
            for (int j = regla.simbolos.size() - 1; j >= 0; j--) {
                pendientes.push(regla.simbolos.get(j));
            }
        }
        while (!pendientes.isEmpty()) {
            String s = pendientes.pop();
            assertFalse(g.esNoTerminal(s), "quedó sin derivar el no terminal " + s);
            derivados.add(s);
        }

        List<String> tokens = new ArrayList<>();
        Pattern p = Pattern.compile("<(\\w+),");
        for (String linea : Files.readAllLines(salida.resolve("tokens.txt"), StandardCharsets.UTF_8)) {
            Matcher m = p.matcher(linea);
            assertTrue(m.find(), "token con formato incorrecto: " + linea);
            tokens.add(Token.Codigo.valueOf(m.group(1)).terminal);
        }
        assertEquals(tokens, derivados);
    }

    @Test
    void laTablaDeSimbolosTieneAmbitosYAtributosDeFuncion() throws IOException {
        procesar("correcto4");
        String ts = new String(Files.readAllBytes(salida.resolve("ts.txt")), StandardCharsets.UTF_8);
        // Las tablas locales se vuelcan antes que la global (al destruirse)
        assertTrue(ts.indexOf("TABLA DE LA FUNCION factorial #2:") < ts.indexOf("TABLA PRINCIPAL #1:"));
        assertTrue(ts.contains("+ numParam : 1"));
        assertTrue(ts.contains("+ TipoParam01 : 'entero'"));
        assertTrue(ts.contains("+ TipoRetorno : 'vacio'"));
        assertTrue(ts.contains("* LEXEMA : 'sinDeclarar'"), "la variable implícita debe estar en la TS global");
    }

    @Test
    void elFicheroDeTokensUsaElFormatoOficial() throws IOException {
        procesarTexto("let float x = 2.50;\nwrite \"hola\";\n");
        List<String> tokens = Files.readAllLines(salida.resolve("tokens.txt"), StandardCharsets.UTF_8);
        assertEquals(List.of("<LET,>", "<FLOAT,>", "<ID,0>", "<IGUAL,>", "<NUMREAL,2.50>", "<PUNTOYCOMA,>",
                "<WRITE,>", "<CADENA,\"hola\">", "<PUNTOYCOMA,>", "<EOF,>"), tokens);
    }

    // ------------------------------------------------------------------
    // Casos con errores
    // ------------------------------------------------------------------

    @Test
    void erroresLexicos() throws IOException {
        List<String> m = mensajes(procesar("error1"));
        assertTrue(m.contains("Error léxico (línea 4): La constante entera 40000 supera el máximo permitido (32767)"));
        assertTrue(m.contains("Error léxico (línea 5): La constante real '3.' debe tener al menos una cifra decimal"));
        assertTrue(m.contains("Error léxico (línea 6): La cadena tiene 84 caracteres y el máximo es 64"));
        assertTrue(m.contains("Error léxico (línea 7): El carácter '#' no pertenece al lenguaje"));
        assertTrue(m.contains("Error léxico (línea 8): Cadena sin cerrar: falta la comilla doble final"));
    }

    @Test
    void errorSintacticoFaltaPuntoYComa() throws IOException {
        Main.Resultado r = procesar("error2");
        assertFalse(r.sintaxisCorrecta);
        assertEquals(1, r.errores.getErrores().size());
        GestorErrores.Error e = r.errores.getErrores().get(0);
        assertEquals(GestorErrores.Tipo.SINTACTICO, e.tipo);
        assertEquals(4, e.linea);
        assertTrue(e.mensaje.contains("'write'") && e.mensaje.contains("';'"));
    }

    @Test
    void errorSintacticoEnCabeceraDeFuncionSinErroresEnCascada() throws IOException {
        Main.Resultado r = procesar("error3");
        assertEquals(1, r.errores.getErrores().size(), mensajes(r).toString());
        assertTrue(mensajes(r).get(0).startsWith("Error sintáctico (línea 3): Se encontró '{'"));
    }

    @Test
    void erroresDeTipos() throws IOException {
        List<String> m = mensajes(procesar("error4"));
        assertEquals(8, m.size(), m.toString());
        assertTrue(m.get(0).contains("línea 7") && m.get(0).contains("tipo float a la variable 'n'"));
        assertTrue(m.get(3).contains("condición de 'if' debe ser de tipo boolean"));
        assertTrue(m.get(7).contains("'/=' solo se aplica a variables int o float"));
    }

    @Test
    void erroresDeFuncionesYAmbitos() throws IOException {
        List<String> m = mensajes(procesar("error5"));
        assertEquals(List.of(
                "Error semántico (línea 3): El identificador 'a' ya está declarado en este ámbito",
                "Error semántico (línea 12): La función 'nada' es void y no puede devolver un valor",
                "Error semántico (línea 17): La función 'sinValor' debe devolver un valor de tipo int",
                "Error semántico (línea 20): La función 'cuadrado' espera 1 argumento(s) y se le pasan 2",
                "Error semántico (línea 21): El argumento 1 de 'cuadrado' debe ser de tipo int y es de tipo float",
                "Error semántico (línea 22): La función 'nada' es void: no devuelve ningún valor y no puede usarse en una expresión",
                "Error semántico (línea 23): La función 'noExiste' no está declarada",
                "Error semántico (línea 24): 'cuadrado' es una función: para usarla hay que llamarla con paréntesis",
                "Error semántico (línea 25): 'a' es una variable de tipo int, no una función",
                "Error semántico (línea 26): La sentencia return solo puede usarse dentro de una función"), m);
    }

    @Test
    void lasVariablesLocalesNoSonVisiblesFueraDeSuFuncion() throws IOException {
        Main.Resultado r = procesarTexto(
                "function void f(void) { let string local; local = \"x\"; }\n"
                        + "local = 3;\n");   // fuera de f, 'local' es una global int implícita
        assertEquals(List.of(), mensajes(r));
    }

    @Test
    void sePermiteLaRecursividad() throws IOException {
        Main.Resultado r = procesarTexto("function int f(int n) { return f(n); }\n");
        assertEquals(List.of(), mensajes(r));
    }
}
