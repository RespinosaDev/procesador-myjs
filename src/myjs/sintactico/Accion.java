package myjs.sintactico;

/**
 * Acción semántica intercalada en una regla.
 *
 * @param padre nodo del antecedente de la regla
 * @param hijos nodos de los símbolos del consecuente (sin contar las acciones), en orden
 */
@FunctionalInterface
public interface Accion {
    void ejecutar(Nodo padre, Nodo[] hijos);
}
