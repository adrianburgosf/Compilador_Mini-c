int main() {
  int i = 1;
  int s = 0;
  while (i <= 3) {
    s = s + i;   // 1+2+3 = 6
    i = i + 1;
  }
  printInt(s);      // 6
  printChar('\n');
  return s;
}
