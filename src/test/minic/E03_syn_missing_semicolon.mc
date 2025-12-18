// E03_syn_missing_semicolon.mc
// Purpose: Syntax error (missing semicolon).
int main(){
    int x = 10   // ERROR: missing ';' here
    print_int(x);
    println();
    return 0;
}
