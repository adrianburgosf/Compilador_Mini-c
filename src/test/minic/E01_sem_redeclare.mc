// E01_sem_redeclare.mc
// Purpose: Semantic error (redeclaration in the same scope).
int main(){
    int x = 1;
    int x = 2; // ERROR: redeclaration of x in the same scope
    print_int(x);
    println();
    return 0;
}
