package com.shrey.androiddex.standalone.data

import java.util.Locale
import kotlin.math.abs

/** A bounded arithmetic parser. Never evaluates code or starts a process. */
object Calculator {
    fun evaluate(expression: String): String {
        require(expression.length <= 80) { "Expression is too long" }
        require(expression.isNotBlank()) { "Enter an expression" }
        val parser = Parser(expression)
        val result = parser.parse()
        require(result.isFinite()) { "Result is not finite" }
        if (result == 0.0) return "0"
        return if (abs(result) >= 1e12 || abs(result) < 1e-7) String.format(Locale.US, "%.10g", result)
        else String.format(Locale.US, "%.10f", result).trimEnd('0').trimEnd('.')
    }

    private class Parser(private val input: String) {
        private var position = 0
        fun parse(): Double = expression().also {
            skipSpace()
            require(position == input.length) { "Unexpected character at ${position + 1}" }
        }
        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpace()
                value = when (peek()) {
                    '+' -> { position++; value + term() }
                    '-' -> { position++; value - term() }
                    else -> return value
                }
            }
        }
        private fun term(): Double {
            var value = unary()
            while (true) {
                skipSpace()
                value = when (peek()) {
                    '*' -> { position++; value * unary() }
                    '/' -> { position++; val divisor = unary(); require(divisor != 0.0) { "Cannot divide by zero" }; value / divisor }
                    '%' -> { position++; val divisor = unary(); require(divisor != 0.0) { "Cannot divide by zero" }; value % divisor }
                    else -> return value
                }
            }
        }
        private fun unary(): Double {
            skipSpace()
            return when (peek()) {
                '-' -> { position++; -unary() }
                '+' -> { position++; unary() }
                '(' -> { position++; val value = expression(); skipSpace(); require(peek() == ')') { "Missing closing parenthesis" }; position++; value }
                else -> number()
            }
        }
        private fun number(): Double {
            skipSpace()
            val start = position
            while (peek()?.isDigit() == true) position++
            if (peek() == '.') { position++; while (peek()?.isDigit() == true) position++ }
            val token = input.substring(start, position)
            require(token.isNotEmpty() && token != ".") { "Expected a number at ${start + 1}" }
            return token.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number")
        }
        private fun skipSpace() { while (peek() == ' ') position++ }
        private fun peek(): Char? = input.getOrNull(position)
    }
}
