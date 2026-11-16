package myjs.semantico;

import myjs.errores.GestorErrores;
import myjs.lexico.AnalizadorLexico;
import myjs.lexico.Token;
import myjs.sintactico.Accion;
import myjs.sintactico.Nodo;
import myjs.ts.Entrada;
import myjs.ts.GestorTS;
import myjs.ts.Tipo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analizador semántico: acciones de la traducción dirigida por la sintaxis.
 *
 * Cada acción está asociada a una posición de una regla de la gramática y recibe el nodo
 * del antecedente ({@code padre}) y los de los símbolos del consecuente ({@code hijos}).
 *
 * Reglas del lenguaje que se comprueban:
 * <ul>
 *   <li>No hay conversiones de tipo: los operandos de {@code +}, {@code /} y {@code <} deben
 *       ser los dos int o los dos float; {@code ==} compara valores del mismo tipo; {@code !}
 *       requiere boolean.</li>
 *   <li>Las condiciones de if y while son boolean.</li>
 *   <li>La asignación exige el mismo tipo a ambos lados; {@code /=} solo para int o float.</li>
 *   <li>write admite int, float o string; read, variables de esos tipos.</li>
 *   <li>Las llamadas deben coincidir en número y tipo de argumentos; una función void no puede
 *       usarse dentro de una expresión.</li>
 *   <li>return solo dentro de funciones y con el tipo de retorno declarado.</li>
 *   <li>Una variable usada sin declarar es una global de tipo int.</li>
 * </ul>
 */
public final class AnalizadorSemantico {

    private final GestorTS ts;
    private final GestorErrores errores;
    private final AnalizadorLexico lexico;

    /** Función que se está analizando (null en el ámbito global). */
    private Entrada funcionActual;
    private Tipo retornoActual;
    private String nombreFuncionActual;
    private int contadorEtiquetas = 0;

    public AnalizadorSemantico(GestorTS ts, GestorErrores errores, AnalizadorLexico lexico) {
        this.ts = ts;
        this.errores = errores;
        this.lexico = lexico;
    }

    /** Acciones semánticas por nombre, tal como aparecen en las reglas de la gramática. */
    public Map<String, Accion> acciones() {
        Map<String, Accion> a = new HashMap<>();

        // ---------------- Declaraciones ----------------
        a.put("zonaDeclaracion", (p, h) -> lexico.setZonaDeclaracion(true));

        // B -> let T id {declararVariable} I ;
        a.put("declararVariable", (p, h) -> {
            lexico.setZonaDeclaracion(false);
            Entrada e = h[2].token.entrada;
            if (e != null) {
                e.tabla.asignarTipoVariable(e, h[1].tipo);
            }
            h[3].heredado = h[1].tipo;
        });
        // I -> = E
        a.put("comprobarInicializacion", (p, h) -> {
            Tipo esperado = (Tipo) p.heredado;
            if (!compatibles(esperado, h[1].tipo)) {
                errores.semantico(h[0].token.linea, "No se puede inicializar una variable de tipo "
                        + esperado.enFuente() + " con un valor de tipo " + h[1].tipo.enFuente());
            }
        });

        a.put("tipoEntero", (p, h) -> p.tipo = Tipo.ENTERO);
        a.put("tipoReal", (p, h) -> p.tipo = Tipo.REAL);
        a.put("tipoLogico", (p, h) -> p.tipo = Tipo.LOGICO);
        a.put("tipoCadena", (p, h) -> p.tipo = Tipo.CADENA);
        a.put("tipoVacio", (p, h) -> p.tipo = Tipo.VACIO);
        a.put("tipoDeT", (p, h) -> p.tipo = h[0].tipo);
        a.put("tipoDeExpresion", (p, h) -> p.tipo = h[0].tipo);

        // ---------------- Funciones ----------------
        // F -> function H id {declararFuncion} ( A ) {finParametros} { C } {finFuncion}
        a.put("declararFuncion", (p, h) -> {
            Token id = h[2].token;
            retornoActual = h[1].tipo;
            nombreFuncionActual = id.lexema;
            funcionActual = id.entrada;   // null si el nombre ya estaba declarado
            if (funcionActual != null) {
                funcionActual.tipo = Tipo.FUNCION;
                funcionActual.tipoRetorno = retornoActual;
                funcionActual.etiqueta = String.format("Et%s%02d", id.lexema, ++contadorEtiquetas);
            }
            ts.crearTablaLocal(id.lexema);
            // La zona de declaración sigue activa para los parámetros.
        });
        a.put("declararParametro", (p, h) -> declararParametro(h[0].tipo, h[1].token));
        a.put("declararParametroK", (p, h) -> declararParametro(h[1].tipo, h[2].token));
        a.put("finParametros", (p, h) -> lexico.setZonaDeclaracion(false));
        a.put("finFuncion", (p, h) -> {
            ts.destruirTablaLocal();
            funcionActual = null;
            retornoActual = null;
            nombreFuncionActual = null;
        });

        // ---------------- Sentencias ----------------
        a.put("comprobarCondicion", (p, h) -> {
            Tipo t = h[2].tipo;
            if (t != Tipo.ERROR && t != Tipo.LOGICO) {
                errores.semantico(h[0].token.linea, "La condición de '" + h[0].token.lexema
                        + "' debe ser de tipo boolean y es de tipo " + t.enFuente());
            }
        });
        a.put("heredarIdentificador", (p, h) -> h[1].heredado = h[0].token);

        // S2 -> = E ;
        a.put("comprobarAsignacion", (p, h) -> {
            Token id = (Token) p.heredado;
            Tipo var = tipoDeVariable(id);
            if (var != Tipo.ERROR && !compatibles(var, h[1].tipo)) {
                errores.semantico(h[0].token.linea, "No se puede asignar un valor de tipo " + h[1].tipo.enFuente()
                        + " a la variable '" + id.lexema + "', que es de tipo " + var.enFuente());
            }
        });
        // S2 -> /= E ;
        a.put("comprobarDivAsignacion", (p, h) -> {
            Token id = (Token) p.heredado;
            Tipo var = tipoDeVariable(id);
            Tipo exp = h[1].tipo;
            if (var == Tipo.ERROR || exp == Tipo.ERROR) {
                return;
            }
            if (var != Tipo.ENTERO && var != Tipo.REAL) {
                errores.semantico(h[0].token.linea, "El operador '/=' solo se aplica a variables int o float y '"
                        + id.lexema + "' es de tipo " + var.enFuente());
            } else if (var != exp) {
                errores.semantico(h[0].token.linea, "En '" + id.lexema + " /= ...' la expresión debe ser de tipo "
                        + var.enFuente() + " y es de tipo " + exp.enFuente());
            }
        });
        // S2 -> ( L ) ;
        a.put("comprobarLlamadaSentencia", (p, h) -> comprobarLlamada((Token) p.heredado, h[1].lista));

        a.put("comprobarWrite", (p, h) -> {
            Tipo t = h[1].tipo;
            if (t != Tipo.ERROR && t != Tipo.ENTERO && t != Tipo.REAL && t != Tipo.CADENA) {
                errores.semantico(h[0].token.linea, "write solo puede mostrar valores int, float o string, no "
                        + t.enFuente());
            }
        });
        a.put("comprobarRead", (p, h) -> {
            Token id = h[1].token;
            Tipo t = tipoDeVariable(id);
            if (t != Tipo.ERROR && t != Tipo.ENTERO && t != Tipo.REAL && t != Tipo.CADENA) {
                errores.semantico(id.linea, "read solo puede leer variables int, float o string y '"
                        + id.lexema + "' es de tipo " + t.enFuente());
            }
        });
        a.put("comprobarReturn", (p, h) -> {
            int linea = h[0].token.linea;
            Tipo t = h[1].tipo;
            if (retornoActual == null) {
                errores.semantico(linea, "La sentencia return solo puede usarse dentro de una función");
            } else if (t == Tipo.ERROR) {
                // ya se informó del error de la expresión
            } else if (retornoActual == Tipo.VACIO && t != Tipo.VACIO) {
                errores.semantico(linea, "La función '" + nombreFuncionActual + "' es void y no puede devolver un valor");
            } else if (retornoActual != Tipo.VACIO && t == Tipo.VACIO) {
                errores.semantico(linea, "La función '" + nombreFuncionActual + "' debe devolver un valor de tipo "
                        + retornoActual.enFuente());
            } else if (t != retornoActual) {
                errores.semantico(linea, "La función '" + nombreFuncionActual + "' debe devolver un valor de tipo "
                        + retornoActual.enFuente() + " y se devuelve uno de tipo " + t.enFuente());
            }
        });

        // ---------------- Argumentos de llamadas ----------------
        a.put("iniciarArgumentos", (p, h) -> h[1].heredado = new ArrayList<>(Collections.singletonList(h[0].tipo)));
        a.put("sintetizarArgumentos", (p, h) -> p.lista = h[1].lista);
        a.put("sinArgumentos", (p, h) -> p.lista = new ArrayList<>());
        a.put("anadirArgumento", (p, h) -> {
            @SuppressWarnings("unchecked")
            List<Tipo> l = new ArrayList<>((List<Tipo>) p.heredado);
            l.add(h[1].tipo);
            h[2].heredado = l;
        });
        a.put("sintetizarArgumentosQ", (p, h) -> p.lista = h[2].lista);
        a.put("cerrarArgumentos", (p, h) -> {
            @SuppressWarnings("unchecked")
            List<Tipo> l = (List<Tipo>) p.heredado;
            p.lista = l;
        });

        // ---------------- Expresiones ----------------
        Accion heredarTipo = (p, h) -> h[1].heredado = h[0].tipo;
        Accion sintetizarDelSegundo = (p, h) -> p.tipo = h[1].tipo;
        a.put("heredarE2", heredarTipo);
        a.put("heredarR2", heredarTipo);
        a.put("heredarU2", heredarTipo);
        a.put("sintetizarE", sintetizarDelSegundo);
        a.put("sintetizarR", sintetizarDelSegundo);
        a.put("sintetizarU", sintetizarDelSegundo);
        a.put("sintetizarV", sintetizarDelSegundo);
        a.put("sinOperador", (p, h) -> p.tipo = (Tipo) p.heredado);
        a.put("sintetizarResto", (p, h) -> p.tipo = h[2].tipo);

        a.put("comparacionMenor", (p, h) -> p.tipo = relacional((Tipo) p.heredado, h[1].tipo, h[0].token));
        a.put("comparacionIgual", (p, h) -> p.tipo = igualdad((Tipo) p.heredado, h[1].tipo, h[0].token));
        a.put("sumar", (p, h) -> h[2].heredado = aritmetica((Tipo) p.heredado, h[1].tipo, h[0].token));
        a.put("dividir", (p, h) -> h[2].heredado = aritmetica((Tipo) p.heredado, h[1].tipo, h[0].token));
        a.put("negar", (p, h) -> {
            Tipo t = h[1].tipo;
            if (t == Tipo.ERROR) {
                p.tipo = Tipo.ERROR;
            } else if (t != Tipo.LOGICO) {
                errores.semantico(h[0].token.linea, "El operador '!' requiere un operando boolean y se aplica a uno de tipo "
                        + t.enFuente());
                p.tipo = Tipo.ERROR;
            } else {
                p.tipo = Tipo.LOGICO;
            }
        });
        a.put("parentesis", (p, h) -> p.tipo = h[1].tipo);
        a.put("constanteEntera", (p, h) -> p.tipo = Tipo.ENTERO);
        a.put("constanteReal", (p, h) -> p.tipo = Tipo.REAL);
        a.put("constanteCadena", (p, h) -> p.tipo = Tipo.CADENA);

        // V2 -> ( L )
        a.put("llamadaEnExpresion", (p, h) -> {
            Token id = (Token) p.heredado;
            Tipo t = comprobarLlamada(id, h[1].lista);
            if (t == Tipo.VACIO) {
                errores.semantico(id.linea, "La función '" + id.lexema
                        + "' es void: no devuelve ningún valor y no puede usarse en una expresión");
                t = Tipo.ERROR;
            }
            p.tipo = t;
        });
        // V2 -> lambda
        a.put("variableEnExpresion", (p, h) -> p.tipo = tipoDeVariable((Token) p.heredado));

        return a;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void declararParametro(Tipo tipo, Token id) {
        if (id.entrada != null) {
            id.entrada.tabla.asignarTipoVariable(id.entrada, tipo);
        }
        if (funcionActual != null) {
            funcionActual.tiposParam.add(tipo);
        }
    }

    /** Tipo de un identificador usado como variable. Una variable sin declarar pasa a ser global int. */
    private Tipo tipoDeVariable(Token id) {
        Entrada e = id.entrada;
        if (e == null) {
            return Tipo.ERROR;
        }
        if (e.tipo == null) {
            e.tabla.asignarTipoVariable(e, Tipo.ENTERO);
        }
        if (e.esFuncion()) {
            errores.semantico(id.linea, "'" + id.lexema + "' es una función: para usarla hay que llamarla con paréntesis");
            return Tipo.ERROR;
        }
        return e.tipo;
    }

    /** Comprueba una llamada y devuelve el tipo de retorno de la función (o ERROR). */
    private Tipo comprobarLlamada(Token id, List<Tipo> argumentos) {
        Entrada e = id.entrada;
        if (e == null) {
            return Tipo.ERROR;
        }
        if (e.tipo == null) {
            errores.semantico(id.linea, "La función '" + id.lexema + "' no está declarada");
            return Tipo.ERROR;
        }
        if (!e.esFuncion()) {
            errores.semantico(id.linea, "'" + id.lexema + "' es una variable de tipo " + e.tipo.enFuente()
                    + ", no una función");
            return Tipo.ERROR;
        }
        if (argumentos.size() != e.tiposParam.size()) {
            errores.semantico(id.linea, "La función '" + id.lexema + "' espera " + e.tiposParam.size()
                    + " argumento(s) y se le pasan " + argumentos.size());
            return e.tipoRetorno;
        }
        for (int i = 0; i < argumentos.size(); i++) {
            Tipo dado = argumentos.get(i);
            Tipo esperado = e.tiposParam.get(i);
            if (dado != Tipo.ERROR && dado != esperado) {
                errores.semantico(id.linea, "El argumento " + (i + 1) + " de '" + id.lexema + "' debe ser de tipo "
                        + esperado.enFuente() + " y es de tipo " + dado.enFuente());
            }
        }
        return e.tipoRetorno;
    }

    private static boolean compatibles(Tipo a, Tipo b) {
        return a == Tipo.ERROR || b == Tipo.ERROR || a == b;
    }

    private static boolean numerico(Tipo t) {
        return t == Tipo.ENTERO || t == Tipo.REAL;
    }

    private Tipo aritmetica(Tipo izq, Tipo der, Token op) {
        if (izq == Tipo.ERROR || der == Tipo.ERROR) {
            return Tipo.ERROR;
        }
        if (numerico(izq) && izq == der) {
            return izq;
        }
        errores.semantico(op.linea, "El operador '" + op.lexema + "' requiere dos operandos int o dos float"
                + " y se aplica a " + izq.enFuente() + " y " + der.enFuente());
        return Tipo.ERROR;
    }

    private Tipo relacional(Tipo izq, Tipo der, Token op) {
        if (izq == Tipo.ERROR || der == Tipo.ERROR) {
            return Tipo.ERROR;
        }
        if (numerico(izq) && izq == der) {
            return Tipo.LOGICO;
        }
        errores.semantico(op.linea, "El operador '<' compara dos int o dos float y se aplica a "
                + izq.enFuente() + " y " + der.enFuente());
        return Tipo.ERROR;
    }

    private Tipo igualdad(Tipo izq, Tipo der, Token op) {
        if (izq == Tipo.ERROR || der == Tipo.ERROR) {
            return Tipo.ERROR;
        }
        if (izq == der && izq != Tipo.VACIO) {
            return Tipo.LOGICO;
        }
        errores.semantico(op.linea, "El operador '==' compara valores del mismo tipo y se aplica a "
                + izq.enFuente() + " y " + der.enFuente());
        return Tipo.ERROR;
    }
}
