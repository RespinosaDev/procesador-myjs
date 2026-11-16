// Caso erróneo 5: errores en funciones, llamadas y ámbitos
let int a;
let int a;

function int cuadrado(int x)
{
	return x + x;
}

function void nada(void)
{
	return 5;
}

function int sinValor(void)
{
	return;
}

a = cuadrado(1, 2);
a = cuadrado(2.0);
a = nada();
a = noExiste(3);
a = cuadrado;
a(4);
return a;
