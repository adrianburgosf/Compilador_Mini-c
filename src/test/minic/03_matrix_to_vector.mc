// 03_matrix_to_vector.mc
// Purpose: 2D global array, nested for loops, arithmetic + mod, flatten to 1D array, while loop printing.
// Expected console output: 50 lines "a[i] = <value>" then the final message.
// First lines:
//   a[1] = 0
//   a[2] = 14
//   a[3] = 13
// ...
// Last lines:
//   a[48] = 13
//   a[49] = 12
//   a[50] = 11
//   Gracias por usar Mini-C!
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

    print_str("Gracias por usar Mini-C!\n");
    return 0;
}
