int g = 1;

int sum2(int a, int b) {
  int t = a + b;
  return t;
}

int main() {
  int v = sum2(3, 4);
  { int x = v; }
  return v;
}
