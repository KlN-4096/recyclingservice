package com.klnon.recyclingservice.foundation.utility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ExpressionEvaluator {
    private static final Map<String, CompiledExpression> CACHE = new ConcurrentHashMap<>();

    private ExpressionEvaluator() {
    }

    public static double evaluate(String expression, Map<String, Double> variables) {
        if (expression == null || expression.isBlank()) {
            return 0D;
        }
        CompiledExpression compiled = CACHE.computeIfAbsent(expression, ExpressionEvaluator::compile);
        return compiled.evaluate(variables);
    }

    private static CompiledExpression compile(String expression) {
        List<Token> output = new ArrayList<>();
        Deque<Token> operators = new ArrayDeque<>();
        Deque<Integer> argCountStack = new ArrayDeque<>();
        TokenType previous = null;
        boolean functionPending = false;

        int index = 0;
        String source = expression.trim();
        while (index < source.length()) {
            char ch = source.charAt(index);
            if (Character.isWhitespace(ch)) {
                index++;
                continue;
            }

            if (isNumberStart(ch, index, source)) {
                int start = index;
                index++;
                while (index < source.length() && isNumberPart(source.charAt(index))) {
                    index++;
                }
                String number = source.substring(start, index);
                output.add(Token.number(Double.parseDouble(number)));
                previous = TokenType.NUMBER;
                continue;
            }

            if (isIdentifierStart(ch)) {
                int start = index;
                index++;
                while (index < source.length() && isIdentifierPart(source.charAt(index))) {
                    index++;
                }
                String name = source.substring(start, index);
                int lookahead = skipWhitespace(source, index);
                if (lookahead < source.length() && source.charAt(lookahead) == '(') {
                    operators.push(Token.function(name));
                    functionPending = true;
                    previous = TokenType.FUNCTION;
                } else {
                    output.add(Token.variable(name));
                    previous = TokenType.VARIABLE;
                }
                continue;
            }

            if (ch == '(') {
                operators.push(Token.leftParen());
                if (functionPending) {
                    argCountStack.push(0);
                    functionPending = false;
                } else {
                    argCountStack.push(-1);
                }
                index++;
                previous = TokenType.LEFT_PAREN;
                continue;
            }

            if (ch == ')') {
                while (!operators.isEmpty() && operators.peek().type != TokenType.LEFT_PAREN) {
                    output.add(operators.pop());
                }
                if (operators.isEmpty() || operators.peek().type != TokenType.LEFT_PAREN) {
                    throw new IllegalArgumentException("Mismatched parentheses in expression: " + expression);
                }
                operators.pop();
                if (argCountStack.isEmpty()) {
                    throw new IllegalArgumentException("Invalid function arguments in expression: " + expression);
                }
                int argCount = argCountStack.pop();
                if (argCount >= 0) {
                    if (operators.isEmpty() || operators.peek().type != TokenType.FUNCTION) {
                        throw new IllegalArgumentException("Missing function name in expression: " + expression);
                    }
                    Token function = operators.pop();
                    output.add(function.withArgCount(argCount + 1));
                }
                index++;
                previous = TokenType.RIGHT_PAREN;
                continue;
            }

            if (ch == ',') {
                while (!operators.isEmpty() && operators.peek().type != TokenType.LEFT_PAREN) {
                    output.add(operators.pop());
                }
                if (argCountStack.isEmpty() || argCountStack.peek() < 0) {
                    throw new IllegalArgumentException("Unexpected comma in expression: " + expression);
                }
                argCountStack.push(argCountStack.pop() + 1);
                index++;
                previous = TokenType.COMMA;
                continue;
            }

            if (isOperator(ch)) {
                String symbol = String.valueOf(ch);
                if (ch == '-' && (previous == null || previous == TokenType.OPERATOR
                        || previous == TokenType.LEFT_PAREN || previous == TokenType.COMMA)) {
                    symbol = "NEG";
                }
                Token current = Token.operator(symbol);
                while (!operators.isEmpty() && operators.peek().type == TokenType.OPERATOR) {
                    Token top = operators.peek();
                    if ((current.isLeftAssociative() && current.precedence <= top.precedence)
                            || (!current.isLeftAssociative() && current.precedence < top.precedence)) {
                        output.add(operators.pop());
                    } else {
                        break;
                    }
                }
                operators.push(current);
                index++;
                previous = TokenType.OPERATOR;
                continue;
            }

            throw new IllegalArgumentException("Unexpected character '" + ch + "' in expression: " + expression);
        }

        while (!operators.isEmpty()) {
            Token token = operators.pop();
            if (token.type == TokenType.LEFT_PAREN || token.type == TokenType.RIGHT_PAREN) {
                throw new IllegalArgumentException("Mismatched parentheses in expression: " + expression);
            }
            output.add(token);
        }

        return new CompiledExpression(output);
    }

    private static boolean isNumberStart(char ch, int index, String source) {
        if (Character.isDigit(ch)) {
            return true;
        }
        return ch == '.' && index + 1 < source.length() && Character.isDigit(source.charAt(index + 1));
    }

    private static boolean isNumberPart(char ch) {
        return Character.isDigit(ch) || ch == '.';
    }

    private static boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static boolean isOperator(char ch) {
        return ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '%' || ch == '^';
    }

    private static int skipWhitespace(String source, int index) {
        int pos = index;
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private enum TokenType {
        NUMBER,
        VARIABLE,
        OPERATOR,
        FUNCTION,
        LEFT_PAREN,
        RIGHT_PAREN,
        COMMA
    }

    private static final class Token {
        private final TokenType type;
        private final String text;
        private final double value;
        private final int argCount;
        private final int precedence;
        private final boolean leftAssociative;

        private Token(TokenType type, String text, double value, int argCount, int precedence, boolean leftAssociative) {
            this.type = type;
            this.text = text;
            this.value = value;
            this.argCount = argCount;
            this.precedence = precedence;
            this.leftAssociative = leftAssociative;
        }

        static Token number(double value) {
            return new Token(TokenType.NUMBER, null, value, 0, 0, true);
        }

        static Token variable(String name) {
            return new Token(TokenType.VARIABLE, name, 0, 0, 0, true);
        }

        static Token operator(String symbol) {
            Operator op = Operator.fromSymbol(symbol);
            return new Token(TokenType.OPERATOR, symbol, 0, op.arity, op.precedence, op.leftAssociative);
        }

        static Token function(String name) {
            return new Token(TokenType.FUNCTION, name, 0, 0, 0, true);
        }

        static Token leftParen() {
            return new Token(TokenType.LEFT_PAREN, null, 0, 0, 0, true);
        }

        Token withArgCount(int count) {
            return new Token(type, text, value, count, precedence, leftAssociative);
        }

        boolean isLeftAssociative() {
            return leftAssociative;
        }
    }

    private record Operator(int precedence, boolean leftAssociative, int arity) {
        static Operator fromSymbol(String symbol) {
            return switch (symbol) {
                case "+", "-" -> new Operator(2, true, 2);
                case "*", "/", "%" -> new Operator(3, true, 2);
                case "^" -> new Operator(4, false, 2);
                case "NEG" -> new Operator(5, false, 1);
                default -> throw new IllegalArgumentException("Unknown operator: " + symbol);
            };
        }
    }

    private static final class CompiledExpression {
        private final List<Token> rpn;

        private CompiledExpression(List<Token> rpn) {
            this.rpn = rpn;
        }

        double evaluate(Map<String, Double> variables) {
            Deque<Double> stack = new ArrayDeque<>();
            for (Token token : rpn) {
                switch (token.type) {
                    case NUMBER -> stack.push(token.value);
                    case VARIABLE -> stack.push(resolveVariable(token.text, variables));
                    case OPERATOR -> applyOperator(token, stack);
                    case FUNCTION -> applyFunction(token, stack);
                    default -> throw new IllegalArgumentException("Unexpected token: " + token.type);
                }
            }
            if (stack.size() != 1) {
                throw new IllegalArgumentException("Invalid expression");
            }
            return stack.pop();
        }

        private double resolveVariable(String name, Map<String, Double> variables) {
            if (variables == null) {
                return 0D;
            }
            Double value = variables.get(normalize(name));
            return value != null ? value : 0D;
        }

        private void applyOperator(Token token, Deque<Double> stack) {
            if (token.argCount == 1) {
                double value = pop(stack);
                stack.push(-value);
                return;
            }
            double right = pop(stack);
            double left = pop(stack);
            stack.push(switch (token.text) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*" -> left * right;
                case "/" -> right == 0D ? 0D : left / right;
                case "%" -> right == 0D ? 0D : left % right;
                case "^" -> Math.pow(left, right);
                default -> throw new IllegalArgumentException("Unknown operator: " + token.text);
            });
        }

        private void applyFunction(Token token, Deque<Double> stack) {
            String name = normalize(token.text);
            int argCount = token.argCount;
            if (argCount <= 0) {
                throw new IllegalArgumentException("Function requires arguments: " + name);
            }
            double[] args = new double[argCount];
            for (int i = argCount - 1; i >= 0; i--) {
                args[i] = pop(stack);
            }
            stack.push(switch (name) {
                case "min" -> min(args);
                case "max" -> max(args);
                case "floor" -> Math.floor(requireArgs(name, args, 1)[0]);
                case "ceil" -> Math.ceil(requireArgs(name, args, 1)[0]);
                case "abs" -> Math.abs(requireArgs(name, args, 1)[0]);
                case "round" -> Math.rint(requireArgs(name, args, 1)[0]);
                case "step" -> step(requireArgs(name, args, 1)[0]);
                default -> throw new IllegalArgumentException("Unknown function: " + name);
            });
        }

        private double[] requireArgs(String name, double[] args, int expected) {
            if (args.length != expected) {
                throw new IllegalArgumentException("Function " + name + " expects " + expected + " arguments");
            }
            return args;
        }

        private double min(double[] args) {
            double result = args[0];
            for (int i = 1; i < args.length; i++) {
                result = Math.min(result, args[i]);
            }
            return result;
        }

        private double max(double[] args) {
            double result = args[0];
            for (int i = 1; i < args.length; i++) {
                result = Math.max(result, args[i]);
            }
            return result;
        }

        private double step(double value) {
            return value > 0D ? 1D : 0D;
        }

        private double pop(Deque<Double> stack) {
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("Invalid expression");
            }
            return stack.pop();
        }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
