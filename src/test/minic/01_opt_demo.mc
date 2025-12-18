// 01_opt_demo.mc
// Purpose: Demonstrate constant folding with -O --dump-ir.
// Expected console output:
//   x = 24
int main(){
    int x;
    x = (3 + 4) * 2 + 10;
    print_str("x = ");
    print_int(x);
    println();
    return 0;
}
