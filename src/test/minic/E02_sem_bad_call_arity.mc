// E02_sem_bad_call_arity.mc
// Purpose: Semantic error (wrong number of arguments in function call).
int sum2(int a, int b){
    return a + b;
}

int main(){
    int v = sum2(1); // ERROR: expected 2 args, got 1
    print_int(v);
    println();
    return 0;
}
