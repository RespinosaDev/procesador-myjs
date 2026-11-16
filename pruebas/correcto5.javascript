// Caso correcto 5: expresiones anidadas y comparaciones de distintos tipos
let string a = "uno";
let string b = "dos";
let boolean iguales;
let float r;

function boolean mayorQueCero(float valor)
{
	return 0.0 < valor;
}

iguales = a == b;
r = (1.25 + 2.75) / (4.0 + 0.5);
if (mayorQueCero(r)) write "r es positivo";
if (iguales == (1 < 2)) write "comparación de booleanos";
