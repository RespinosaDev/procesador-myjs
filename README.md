# Procesador de lenguaje MyJS · Analizador LL(1)

Procesador para **MyJS** (PdL-JS), un subconjunto de JavaScript con tipos. Está escrito en **Java** a mano, sin usar herramientas que generen el analizador. Hace el **análisis léxico, sintáctico y semántico** de un programa, lleva la **tabla de símbolos** con sus ámbitos y genera los ficheros de tokens, parse, tabla de símbolos y errores en el formato que pedía la asignatura. El parse se puede ver como un árbol con la herramienta VASt.

Es una práctica de la asignatura *Procesadores de Lenguajes* (Grado en Ingeniería Informática, Universidad Politécnica de Madrid). La hicimos en un grupo de 3 personas.

---

## El lenguaje

```javascript
// Funciones con parámetros por valor y tipo de retorno
function int suma(int a, int b)
{
	let int resultado;
	resultado = a + b;
	return resultado;
}

let float precio = 19.95;
let boolean barato = precio < 20.0;
precio /= 2.0;
if (barato) write "¡Oferta!";
while (!barato) { barato = 1 == 1; }
write suma(3, 4);
```

| Elemento | MyJS |
|----------|------|
| Tipos | `int` (2 B, hasta 32767), `float` (4 B), `boolean` (1 B), `string` (64 B), `void` |
| Declaraciones | `let tipo id;` o `let tipo id = expr;` |
| Sentencias | asignación `=`, `/=`, llamada, `return`, `write`, `read`, `if` simple, `while` |
| Operadores | `+` y `/` (aritméticos), `<` y `==` (relacionales), `!` (lógico) |
| Constantes | enteras, reales (`d+.d+`), cadenas `"..."` (máx. 64 caracteres) |
| Comentarios | de línea, `//` |

**Reglas semánticas**: no hay conversiones de tipo; las condiciones son `boolean`; las llamadas deben coincidir en número y tipo de argumentos; una función `void` no puede usarse en una expresión; `return` solo dentro de funciones y con el tipo declarado; y, como en JavaScript, **una variable usada sin declarar se convierte en una variable global de tipo `int`**.

## Arquitectura

```
                     ┌──────────────────── Gestor de errores ────────────────────┐
fuente ──► Léxico ──tokens──► Sintáctico LL(1) ──acciones──► Semántico          │
             │  ▲                 │  (pila extendida)            │               │
             │  └── zona de declaración ─────────────────────────┘               │
             └──────────────► Tabla de símbolos (global + local) ◄───────────────┘
```

- **Léxico** ([`AnalizadorLexico`](src/myjs/lexico/AnalizadorLexico.java)): implementa el autómata finito del diseño. Introduce los identificadores en la tabla de símbolos: en una **zona de declaración** los inserta en el ámbito actual (detectando redeclaraciones) y fuera de ella los busca en el local y después en el global.
- **Sintáctico** ([`AnalizadorSintactico`](src/myjs/sintactico/AnalizadorSintactico.java)): analizador **descendente con tablas** (predictivo no recursivo). La pila contiene terminales, no terminales y **acciones semánticas**, que se ejecutan al desapilarse. Los tokens se piden al léxico solo cuando hacen falta, de modo que cada acción se ejecuta antes de leer el token siguiente.
- **Gramática** ([`Gramatica`](src/myjs/sintactico/Gramatica.java)): 53 reglas con las acciones intercaladas. A partir de ellas se calculan **FIRST y FOLLOW** y se construye la **tabla LL(1)**; si hubiera un conflicto, la construcción fallaría. [`tablaLL1.txt`](tablaLL1.txt) recoge los conjuntos y la tabla como demostración de que la gramática es LL(1).
- **Semántico** ([`AnalizadorSemantico`](src/myjs/semantico/AnalizadorSemantico.java)): traducción dirigida por la sintaxis con atributos **heredados** (el tipo de la parte izquierda de una expresión, el identificador de una llamada) y **sintetizados**. Un tipo `error` interno evita los mensajes en cascada.
- **Tabla de símbolos** ([`GestorTS`](src/myjs/ts/GestorTS.java)): una tabla global y una local por función, con tipo y desplazamiento de cada variable y `numParam`, `TipoParamXX`, `TipoRetorno` y `EtiqFuncion` para las funciones. Cada tabla se vuelca al fichero **justo antes de destruirse**.

## Uso

Requiere **JDK 11** o superior.

```bash
./build.sh                                        # o build.bat en Windows → myjs.jar
java -jar myjs.jar pruebas/correcto2.javascript salida
java -jar myjs.jar --gramatica .                  # regenera gramatica.txt y tablaLL1.txt
```

Ficheros generados:

| Fichero | Contenido |
|---------|-----------|
| `tokens.txt` | Un token por línea: `<LET,>`, `<ID,3>`, `<NUM,10>`, `<CADENA,"hola">` |
| `parse.txt` | `Descendente 1 4 10 9 …`, los números de regla para VASt |
| `ts.txt` | Tablas de símbolos en el formato de la asignatura |
| `errores.txt` | Errores léxicos, sintácticos y semánticos con su línea |

Con [`gramatica.txt`](gramatica.txt) y un `parse.txt`, **VASt** dibuja el árbol sintáctico.

## Pruebas

En [`pruebas/`](pruebas) hay **10 programas**: 5 correctos y 5 con errores (léxicos, sintácticos, de tipos y de funciones y ámbitos). Junto a ellos, en [`pruebas/salida/`](pruebas/salida), están los ficheros que genera el procesador para cada uno. Por ejemplo, [`error5`](pruebas/error5.javascript) produce:

```
Error semántico (línea 20): La función 'cuadrado' espera 1 argumento(s) y se le pasan 2
Error semántico (línea 22): La función 'nada' es void: no devuelve ningún valor y no puede usarse en una expresión
Error semántico (línea 24): 'cuadrado' es una función: para usarla hay que llamarla con paréntesis
```

Los tests automáticos ([`test/`](test/myjs/ProcesadorTest.java), JUnit 5) comprueban:

- que la gramática es LL(1);
- que los casos correctos no dan errores y los erróneos dan exactamente los esperados;
- el formato del fichero de tokens y de la tabla de símbolos;
- que **cada parse, aplicado como derivación por la izquierda, reproduce exactamente la secuencia de tokens**, que es lo mismo que comprueba VASt al dibujar el árbol.

```bash
javac -d build/classes $(find src -name '*.java')
javac -cp build/classes:junit-platform-console-standalone.jar -d build/test $(find test -name '*.java')
java -jar junit-platform-console-standalone.jar execute -cp build/classes:build/test --scan-classpath
```

## Sobre esta versión

El código de la entrega final se nos perdió, así que este repositorio lo **reconstruye** a partir de la primera versión del analizador léxico, de la memoria de diseño del grupo (tokens, autómata y acciones semánticas) y de la documentación de la asignatura sobre formatos y análisis LL. Incluye además las correcciones que hizo el profesor a la primera entrega: los reales deben tener parte decimal, su valor se calcula correctamente y la tabla de símbolos solo la rellena el léxico con lo que le corresponde.
