int sum2(int a, int b) { return a + b; }

int main() {
  int x = sum2(1);        // error: faltan args
  int y = sum2(1, 2, 3);  // error: sobran args
  return 0;
}
