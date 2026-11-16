// Caso correcto 3: bucles while, condiciones y negación
let int i;
let int limite = 5;
let boolean seguir = 1 == 1;

i = 0;
while (i < limite)
{
	if (!(i == 3)) write i;
	i = i + 1;
}

while (seguir)
{
	seguir = !seguir;
}
