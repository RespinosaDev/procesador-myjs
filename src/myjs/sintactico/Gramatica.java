package myjs.sintactico;

import java.io.PrintWriter;
import java.util.*;

/**
 * Gramática LL(1) de MyJS.
 *
 * Cada regla se escribe como texto: {@code "B -> let {zonaDeclaracion} T id {declararVariable} I ;"}.
 * Los símbolos entre llaves son <b>acciones semánticas</b>: no forman parte de la gramática
 * (no aparecen en el fichero de VASt ni en FIRST/FOLLOW), pero se apilan junto a los símbolos
 * para ejecutarse en el momento exacto del análisis (pila extendida).
 *
 * A partir de las reglas se calculan FIRST y FOLLOW y se construye la tabla de análisis LL(1).
 * Si la gramática no fuera LL(1), la construcción de la tabla detecta el conflicto y falla.
 */
public final class Gramatica {

    public static final String LAMBDA = "lambda";
    public static final String AXIOMA = "P";

    /** Regla de producción (numerada desde 1, en el mismo orden que el fichero de VASt). */
    public static final class Regla {
        public final int numero;
        public final String antecedente;
        /** Consecuente con las acciones semánticas incluidas (entre llaves). */
        public final List<String> simbolosConAcciones;
        /** Consecuente sin acciones (vacío si es lambda). */
        public final List<String> simbolos;

        Regla(int numero, String antecedente, List<String> simbolosConAcciones) {
            this.numero = numero;
            this.antecedente = antecedente;
            this.simbolosConAcciones = simbolosConAcciones;
            List<String> s = new ArrayList<>();
            for (String x : simbolosConAcciones) {
                if (!esAccion(x)) {
                    s.add(x);
                }
            }
            this.simbolos = s;
        }

        public String textoVASt() {
            return antecedente + " -> " + (simbolos.isEmpty() ? LAMBDA : String.join(" ", simbolos));
        }
    }

    private static final String[] REGLAS = {
            // ---- Programa: secuencia de sentencias, declaraciones y funciones ----
            "P -> B P",
            "P -> F P",
            "P -> eof",
            // ---- Declaraciones y sentencias compuestas ----
            "B -> let {zonaDeclaracion} T id {declararVariable} I ;",
            "B -> if ( E ) {comprobarCondicion} S",
            "B -> while ( E ) {comprobarCondicion} { C }",
            "B -> S",
            "I -> = E {comprobarInicializacion}",
            "I -> lambda",
            "T -> int {tipoEntero}",
            "T -> float {tipoReal}",
            "T -> boolean {tipoLogico}",
            "T -> string {tipoCadena}",
            // ---- Sentencias simples ----
            "S -> id {heredarIdentificador} S2",
            "S -> write E {comprobarWrite} ;",
            "S -> read id {comprobarRead} ;",
            "S -> return X {comprobarReturn} ;",
            "S2 -> = E {comprobarAsignacion} ;",
            "S2 -> /= E {comprobarDivAsignacion} ;",
            "S2 -> ( L ) {comprobarLlamadaSentencia} ;",
            "X -> E {tipoDeExpresion}",
            "X -> lambda {tipoVacio}",
            "L -> E {iniciarArgumentos} Q {sintetizarArgumentos}",
            "L -> lambda {sinArgumentos}",
            "Q -> , E {anadirArgumento} Q {sintetizarArgumentosQ}",
            "Q -> lambda {cerrarArgumentos}",
            // ---- Funciones ----
            "F -> function {zonaDeclaracion} H id {declararFuncion} ( A ) {finParametros} { C } {finFuncion}",
            "H -> T {tipoDeT}",
            "H -> void {tipoVacio}",
            "A -> T id {declararParametro} K",
            "A -> void",
            "K -> , T id {declararParametroK} K",
            "K -> lambda",
            "C -> B C",
            "C -> lambda",
            // ---- Expresiones (de menor a mayor precedencia: relacionales, +, /, !) ----
            "E -> R {heredarE2} E2 {sintetizarE}",
            "E2 -> < R {comparacionMenor}",
            "E2 -> == R {comparacionIgual}",
            "E2 -> lambda {sinOperador}",
            "R -> U {heredarR2} R2 {sintetizarR}",
            "R2 -> + U {sumar} R2 {sintetizarResto}",
            "R2 -> lambda {sinOperador}",
            "U -> V {heredarU2} U2 {sintetizarU}",
            "U2 -> / V {dividir} U2 {sintetizarResto}",
            "U2 -> lambda {sinOperador}",
            "V -> ! V {negar}",
            "V -> ( E ) {parentesis}",
            "V -> id {heredarIdentificador} V2 {sintetizarV}",
            "V -> entero {constanteEntera}",
            "V -> real {constanteReal}",
            "V -> cadena {constanteCadena}",
            "V2 -> ( L ) {llamadaEnExpresion}",
            "V2 -> lambda {variableEnExpresion}",
    };

    private final List<Regla> reglas = new ArrayList<>();
    private final Set<String> noTerminales = new LinkedHashSet<>();
    private final Set<String> terminales = new LinkedHashSet<>();
    private final Map<String, Set<String>> first = new HashMap<>();
    private final Map<String, Set<String>> follow = new HashMap<>();
    private final Map<String, Map<String, Regla>> tabla = new HashMap<>();

    public Gramatica() {
        int n = 1;
        for (String texto : REGLAS) {
            String[] partes = texto.split("->");
            String ant = partes[0].trim();
            List<String> cons = new ArrayList<>();
            for (String s : partes[1].trim().split("\\s+")) {
                if (!s.equals(LAMBDA)) {
                    cons.add(s);
                }
            }
            reglas.add(new Regla(n++, ant, cons));
            noTerminales.add(ant);
        }
        for (Regla r : reglas) {
            for (String s : r.simbolos) {
                if (!noTerminales.contains(s)) {
                    terminales.add(s);
                }
            }
        }
        calcularFirst();
        calcularFollow();
        construirTabla();
    }

    public static boolean esAccion(String simbolo) {
        return simbolo.length() > 2 && simbolo.startsWith("{") && simbolo.endsWith("}");
    }

    public static String nombreAccion(String simbolo) {
        return simbolo.substring(1, simbolo.length() - 1);
    }

    public boolean esNoTerminal(String simbolo) {
        return noTerminales.contains(simbolo);
    }

    public List<Regla> getReglas() {
        return Collections.unmodifiableList(reglas);
    }

    public Regla regla(String noTerminal, String terminal) {
        return tabla.get(noTerminal).get(terminal);
    }

    /** Terminales con los que puede empezar un no terminal (para los mensajes de error). */
    public Set<String> esperados(String noTerminal) {
        return new TreeSet<>(tabla.get(noTerminal).keySet());
    }

    public Set<String> getFirst(String simbolo) {
        return Collections.unmodifiableSet(first.get(simbolo));
    }

    public Set<String> getFollow(String noTerminal) {
        return Collections.unmodifiableSet(follow.get(noTerminal));
    }

    // ------------------------------------------------------------------
    // FIRST, FOLLOW y tabla LL(1)
    // ------------------------------------------------------------------

    private void calcularFirst() {
        for (String t : terminales) {
            first.put(t, new HashSet<>(Collections.singleton(t)));
        }
        for (String nt : noTerminales) {
            first.put(nt, new HashSet<>());
        }
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (Regla r : reglas) {
                cambio |= first.get(r.antecedente).addAll(firstDeCadena(r.simbolos));
            }
        }
    }

    /** FIRST de una secuencia de símbolos (contiene LAMBDA si la secuencia es anulable). */
    public Set<String> firstDeCadena(List<String> simbolos) {
        Set<String> res = new HashSet<>();
        for (String s : simbolos) {
            Set<String> f = first.get(s);
            for (String x : f) {
                if (!x.equals(LAMBDA)) {
                    res.add(x);
                }
            }
            if (!f.contains(LAMBDA)) {
                return res;
            }
        }
        res.add(LAMBDA);
        return res;
    }

    private void calcularFollow() {
        for (String nt : noTerminales) {
            follow.put(nt, new HashSet<>());
        }
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (Regla r : reglas) {
                for (int i = 0; i < r.simbolos.size(); i++) {
                    String b = r.simbolos.get(i);
                    if (!noTerminales.contains(b)) {
                        continue;
                    }
                    Set<String> f = firstDeCadena(r.simbolos.subList(i + 1, r.simbolos.size()));
                    boolean anulable = f.remove(LAMBDA);
                    cambio |= follow.get(b).addAll(f);
                    if (anulable) {
                        cambio |= follow.get(b).addAll(follow.get(r.antecedente));
                    }
                }
            }
        }
    }

    private void construirTabla() {
        for (String nt : noTerminales) {
            tabla.put(nt, new HashMap<>());
        }
        for (Regla r : reglas) {
            Set<String> f = firstDeCadena(r.simbolos);
            Set<String> destinos = new HashSet<>(f);
            if (destinos.remove(LAMBDA)) {
                destinos.addAll(follow.get(r.antecedente));
            }
            for (String t : destinos) {
                Regla previa = tabla.get(r.antecedente).put(t, r);
                if (previa != null) {
                    throw new IllegalStateException("La gramática no es LL(1): conflicto en M[" + r.antecedente
                            + ", " + t + "] entre las reglas " + previa.numero + " y " + r.numero);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Ficheros para VASt y para la memoria
    // ------------------------------------------------------------------

    /** Escribe la gramática en el formato de VASt (las reglas en el mismo orden que el parse). */
    public void escribirVASt(PrintWriter out) {
        out.println("//// Gramatica del lenguaje MyJS (analizador descendente LL(1))");
        out.println("Terminales = { " + String.join(" ", terminales) + " }");
        out.println("NoTerminales = { " + String.join(" ", noTerminales) + " }");
        out.println("Axioma = " + AXIOMA);
        out.println("Producciones = {");
        for (Regla r : reglas) {
            out.println("\t" + r.textoVASt() + "\t//// " + r.numero);
        }
        out.println("}");
    }

    /** Conjuntos FIRST/FOLLOW y tabla LL(1), como demostración de que la gramática es LL(1). */
    public void escribirTablaLL1(PrintWriter out) {
        out.println("CONJUNTOS FIRST Y FOLLOW");
        out.println("========================");
        for (String nt : noTerminales) {
            out.println(nt + ":\tFIRST = " + new TreeSet<>(first.get(nt)) + "\tFOLLOW = " + new TreeSet<>(follow.get(nt)));
        }
        out.println();
        out.println("TABLA DE ANÁLISIS LL(1)  (M[no terminal, terminal] = número de regla)");
        out.println("=====================================================================");
        for (String nt : noTerminales) {
            StringBuilder sb = new StringBuilder(nt + ":");
            for (Map.Entry<String, Regla> e : new TreeMap<>(tabla.get(nt)).entrySet()) {
                sb.append("  ").append(e.getKey()).append("→").append(e.getValue().numero);
            }
            out.println(sb);
        }
        out.println();
        out.println("No hay ninguna celda con más de una regla: la gramática es LL(1).");
    }
}
