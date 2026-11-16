// Caso correcto 2: funciones con parámetros, retorno y llamadas
function int suma(int a, int b)
{
	let int resultado;
	resultado = a + b;
	return resultado;
}

function float media(float x, float y)
{
	return (x + y) / 2.0;
}

function void saludar(string quien)
{
	write "Bienvenido, ";
	write quien;
}

let int total;
total = suma(3, suma(4, 5));
write media(1.5, 2.5);
saludar("Luis");
