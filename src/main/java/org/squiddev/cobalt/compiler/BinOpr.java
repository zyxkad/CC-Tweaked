package org.squiddev.cobalt.compiler;

import static org.squiddev.cobalt.compiler.Lex.*;

enum BinOpr {
	// Arithmetic
	ADD(6, 6),
	SUB(6, 6),
	MUL(7, 7),
	DIV(7, 7),
	MOD(7, 7),
	// Both right associative
	POW(10, 9),
	CONCAT(5, 4),
	// Comparison
	NE(3, 3),
	EQ(3, 3),
	LT(3, 3),
	LE(3, 3),
	GT(3, 3),
	GE(3, 3),
	// Logical
	AND(2, 2),
	OR(1, 1);

	final int left;
	final int right;

	BinOpr(int left, int right) {
		this.left = left;
		this.right = right;
	}

    static BinOpr ofToken(int op) {
        switch (op) {
            case '+':
                return ADD;
            case '-':
                return SUB;
            case '*':
                return MUL;
            case '/':
                return DIV;
            case '%':
                return MOD;
            case '^':
                return POW;
            case TK_CONCAT:
                return CONCAT;
            case TK_NE:
                return NE;
            case TK_EQ:
                return EQ;
            case '<':
                return LT;
            case TK_LE:
                return LE;
            case '>':
                return GT;
            case TK_GE:
                return GE;
            case TK_AND:
                return AND;
            case TK_OR:
                return OR;
            default:
                return null;
        }
    }
}
