.data
.align 2
m:
  .space 200
.align 2
a:
  .space 200
str_0: .asciiz "a["
str_1: .asciiz "] = "
str_2: .asciiz "Gracias por usar MiniC!\n"
.text
.globl __start
__start:
  jal  main
  li   $v0, 10
  syscall

fill:
addiu $sp, $sp, -96
sw   $ra, 0($sp)
sw   $fp, 4($sp)
addiu $fp, $sp, 96
sw   $a0, -4($fp)
sw   $a1, -8($fp)
li   $t0, 1
sw   $t0, -12($fp)
lw   $t0, -4($fp)
sw   $t0, -16($fp)
for_0:
lw   $t0, -16($fp)
li   $t1, 1
slt  $t2, $t0, $t1
xori $t2, $t2, 1
sw   $t2, -20($fp)
lw   $t0, -20($fp)
beq $t0, $zero, endfor_1
lw   $t0, -8($fp)
sw   $t0, -24($fp)
for_2:
lw   $t0, -24($fp)
li   $t1, 1
slt  $t2, $t0, $t1
xori $t2, $t2, 1
sw   $t2, -28($fp)
lw   $t0, -28($fp)
beq $t0, $zero, endfor_3
lw   $t0, -4($fp)
lw   $t1, -8($fp)
subu $t2, $t0, $t1
sw   $t2, -32($fp)
lw   $t0, -12($fp)
lw   $t1, -32($fp)
addu $t2, $t0, $t1
sw   $t2, -36($fp)
lw   $t0, -36($fp)
li   $t1, 5
addu $t2, $t0, $t1
sw   $t2, -40($fp)
lw   $t0, -40($fp)
li   $t1, 15
div  $t0, $t1
mfhi $t2
sw   $t2, -44($fp)
lw   $t0, -16($fp)
li   $t1, 1
subu $t2, $t0, $t1
sw   $t2, -48($fp)
lw   $t0, -24($fp)
li   $t1, 1
subu $t2, $t0, $t1
sw   $t2, -52($fp)
lw   $t0, -48($fp)
li   $t1, 5
mul  $t2, $t0, $t1
sw   $t2, -56($fp)
lw   $t0, -56($fp)
lw   $t1, -52($fp)
addu $t2, $t0, $t1
sw   $t2, -60($fp)
lw   $t0, -60($fp)
li   $t1, 4
mul  $t2, $t0, $t1
sw   $t2, -64($fp)
lw   $t0, -44($fp)
la   $t1, m
lw   $t2, -64($fp)
addu $t1, $t1, $t2
sw   $t0, 0($t1)
lw   $t0, -12($fp)
li   $t1, 1
addu $t2, $t0, $t1
sw   $t2, -68($fp)
lw   $t0, -68($fp)
sw   $t0, -12($fp)
lw   $t0, -24($fp)
li   $t1, 1
subu $t2, $t0, $t1
sw   $t2, -72($fp)
lw   $t0, -72($fp)
sw   $t0, -24($fp)
j for_2
endfor_3:
lw   $t0, -16($fp)
li   $t1, 1
subu $t2, $t0, $t1
sw   $t2, -76($fp)
lw   $t0, -76($fp)
sw   $t0, -16($fp)
j for_0
endfor_1:
li   $v0, 0
lw   $ra, 0($sp)
lw   $fp, 4($sp)
addiu $sp, $sp, 96
jr   $ra

.globl main
main:
addiu $sp, $sp, -112
sw   $ra, 0($sp)
sw   $fp, 4($sp)
addiu $fp, $sp, 112
li   $t0, 10
sw   $t0, -4($fp)
li   $t0, 5
sw   $t0, -8($fp)
li   $t0, 1
sw   $t0, -12($fp)
li   $t0, 50
sw   $t0, -16($fp)
lw   $a0, -4($fp)
lw   $a1, -8($fp)
jal fill
li   $t0, 1
sw   $t0, -12($fp)
li   $t0, 1
sw   $t0, -20($fp)
for_4:
lw   $t0, -20($fp)
lw   $t1, -4($fp)
slt  $t2, $t1, $t0
xori $t2, $t2, 1
sw   $t2, -24($fp)
lw   $t0, -24($fp)
beq $t0, $zero, endfor_5
li   $t0, 1
sw   $t0, -28($fp)
for_6:
lw   $t0, -28($fp)
lw   $t1, -8($fp)
slt  $t2, $t1, $t0
xori $t2, $t2, 1
sw   $t2, -32($fp)
lw   $t0, -32($fp)
beq $t0, $zero, endfor_7
lw   $t0, -20($fp)
li   $t1, 1
subu $t2, $t0, $t1
sw   $t2, -36($fp)
lw   $t0, -28($fp)
li   $t1, 1
subu $t2, $t0, $t1
sw   $t2, -40($fp)
lw   $t0, -36($fp)
li   $t1, 5
mul  $t2, $t0, $t1
sw   $t2, -44($fp)
lw   $t0, -44($fp)
lw   $t1, -40($fp)
addu $t2, $t0, $t1
sw   $t2, -48($fp)
lw   $t0, -48($fp)
li   $t1, 4
mul  $t2, $t0, $t1
sw   $t2, -52($fp)
la   $t1, m
lw   $t2, -52($fp)
addu $t1, $t1, $t2
lw   $t0, 0($t1)
sw   $t0, -56($fp)
lw   $t0, -12($fp)
li   $t1, 1
subu $t2, $t0, $t1
sw   $t2, -60($fp)
lw   $t0, -60($fp)
li   $t1, 4
mul  $t2, $t0, $t1
sw   $t2, -64($fp)
lw   $t0, -56($fp)
la   $t1, a
lw   $t2, -64($fp)
addu $t1, $t1, $t2
sw   $t0, 0($t1)
lw   $t0, -12($fp)
li   $t1, 1
addu $t2, $t0, $t1
sw   $t2, -68($fp)
lw   $t0, -68($fp)
sw   $t0, -12($fp)
lw   $t0, -28($fp)
li   $t1, 1
addu $t2, $t0, $t1
sw   $t2, -72($fp)
lw   $t0, -72($fp)
sw   $t0, -28($fp)
j for_6
endfor_7:
lw   $t0, -20($fp)
li   $t1, 1
addu $t2, $t0, $t1
sw   $t2, -76($fp)
lw   $t0, -76($fp)
sw   $t0, -20($fp)
j for_4
endfor_5:
li   $t0, 1
sw   $t0, -12($fp)
while_8:
lw   $t0, -16($fp)
li   $t1, 1
addu $t2, $t0, $t1
sw   $t2, -80($fp)
lw   $t0, -12($fp)
lw   $t1, -80($fp)
sne  $t2, $t0, $t1
sw   $t2, -84($fp)
lw   $t0, -84($fp)
beq $t0, $zero, endwhile_9
la   $a0, str_0
li $v0, 4
syscall
lw   $a0, -12($fp)
li $v0, 1
syscall
la   $a0, str_1
li $v0, 4
syscall
lw   $t0, -12($fp)
li   $t1, 1
subu $t2, $t0, $t1
sw   $t2, -88($fp)
lw   $t0, -88($fp)
li   $t1, 4
mul  $t2, $t0, $t1
sw   $t2, -92($fp)
la   $t1, a
lw   $t2, -92($fp)
addu $t1, $t1, $t2
lw   $t0, 0($t1)
sw   $t0, -96($fp)
lw   $a0, -96($fp)
li $v0, 1
syscall
li $a0, 10
li $v0, 11
syscall
lw   $t0, -12($fp)
li   $t1, 1
addu $t2, $t0, $t1
sw   $t2, -100($fp)
lw   $t0, -100($fp)
sw   $t0, -12($fp)
j while_8
endwhile_9:
la   $a0, str_2
li $v0, 4
syscall
li   $v0, 0
lw   $ra, 0($sp)
lw   $fp, 4($sp)
addiu $sp, $sp, 112
jr   $ra

