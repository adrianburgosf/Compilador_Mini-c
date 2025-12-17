int main() {
  int a = 10, b = 7;
  int c = (a > b) && (a == 10) || (b == 0); // 1
  printInt(c);   // 1
  printChar('\n');
  return c;
}
