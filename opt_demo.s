.data
.text
.globl __start
__start:
  jal  main
  li   $v0, 10
  syscall

.globl main
main:
addiu $sp, $sp, -32
sw   $ra, 0($sp)
sw   $fp, 4($sp)
addiu $fp, $sp, 32
li   $t0, 7
sw   $t0, -4($fp)
lw   $t0, -4($fp)
li   $t1, 2
mul  $t2, $t0, $t1
sw   $t2, -8($fp)
lw   $t0, -8($fp)
li   $t1, 10
addu $t2, $t0, $t1
sw   $t2, -12($fp)
lw   $t0, -12($fp)
sw   $t0, -16($fp)
lw   $a0, -16($fp)
li $v0, 1
syscall
li $a0, 10
li $v0, 11
syscall
li   $v0, 0
lw   $ra, 0($sp)
lw   $fp, 4($sp)
addiu $sp, $sp, 32
jr   $ra

