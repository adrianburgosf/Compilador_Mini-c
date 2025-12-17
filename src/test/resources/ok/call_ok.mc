int sum2(int a, int b) { return a + b; }

int main() {
  int v = sum2(3, 4);
  printInt(v);        // 7
  printChar('\n');
  return v;           // también $v0 = 7
}
