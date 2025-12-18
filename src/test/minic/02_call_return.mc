// 02_call_return.mc
// Purpose: Function calls, return values, if/else, comparisons.
// Expected console output:
//   w = 7
int sum2(int a, int b){
    return a + b;
}

int max2(int a, int b){
    if (a >= b){
        return a;
    } else {
        return b;
    }
}

int main(){
    int v = sum2(3, 4);
    int w = max2(v, 5);
    print_str("w = ");
    print_int(w);
    println();
    return 0;
}
