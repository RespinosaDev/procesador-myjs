// Caso correcto 4: ámbitos locales, variables globales implícitas y recursividad
let int global;

function int factorial(int n)
{
	let int previo;
	if (n < 2) return 1;
	previo = factorial(n / 2 + menosUno);
	return n / 1 + previo;
}

function void contar(void)
{
	let int global;          // variable local que oculta a la global
	global = 7;
	sinDeclarar = global;    // variable no declarada: se crea como global int
}

menosUno = 0;
global = factorial(4);
contar();
write sinDeclarar;
