// Rellena y vuelca una matriz bidimensional en un vector
int m[10][5];
int a[50];
void fill(int x, int y){
    int i, j, cont = 1;
    for (i = x; i >= 1; i = i - 1){
        for (j = y; j >= 1; j = j - 1){
            m[i][j] = (cont + (x - y) + 5) % 15;
            cont = cont + 1;
        }
    }
}
int main(){
    int i, j, x = 10, y = 5, cont = 1, length = 50;
    fill(x, y);
    cont = 1;
    for (i = 1; i <= x; i = i + 1){
        for (j = 1; j <= y; j = j + 1){
            a[cont] = m[i][j];
            cont = cont + 1;
        }
    }
    cont = 1;
    while (cont != length + 1){
        print_str("a[");
        print_int(cont);
        print_str("] = ");
        print_int(a[cont]);
        println();
        cont = cont + 1;
    }
    print_str("Gracias por usar MiniC!\n");
    return 0;
}