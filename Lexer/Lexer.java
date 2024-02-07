package plc.project;

import java.util.List;
import java.util.ArrayList;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are * helpers you need to use, they will make the implementation a lot easier. */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    public List<Token> lex() {
        List<Token> tokens = new ArrayList<Token>();

        while (peek(".")) {
            if (!match("[ \b\n\r\t]"))
                tokens.add(lexToken());
            else
                chars.skip();
        }

        return tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        if (peek("@|[A-Za-z]"))
            return lexIdentifier();
        else if (peek("[0-9]") || peek("[-]", "[1-9]"))
            return lexInteger();
        else if (peek("-", "0", "\\."))
            return lexDecimal();
        else if (peek("'"))
            return lexCharacter();
        else if (peek("\""))
            return lexString();
        else
            return lexOperator();
    }

    public Token lexIdentifier() {
        match("@|[A-Za-z]");
        while (match("[A-Za-z0-9_-]")) {}
        return chars.emit(Token.Type.IDENTIFIER);
    }

    public Token lexInteger() {
        match("-");
        if (match("0")) {
            if (peek("\\.", "[0-9]"))
                return lexDecimal();
            else
                return chars.emit(Token.Type.INTEGER);
        }
        match("[1-9]");
        do {
            if (peek("\\.", "[0-9]"))
                return lexDecimal();
        } while (match("[0-9]"));
        return chars.emit(Token.Type.INTEGER);
    }

    public Token lexDecimal() {
        //from lexInteger
        if (match("\\.")) {
            while (match("[0-9]")) {}
            return chars.emit(Token.Type.DECIMAL);
        }
        match("-", "0", "\\.");
        while (match("[0-9]")) {}
        return chars.emit(Token.Type.DECIMAL);
    }
    public Token lexCharacter() {
        match("'");
        if (peek("\\\\")) {
            if (!match("\\\\", "[bnrt'\"\\\\]"))
                throw new ParseException("Invalid escape sequence", chars.index + 1);
            if (!match("'"))
                throw new ParseException("Unterminated char", chars.index);
            return chars.emit(Token.Type.CHARACTER);
        }
        if (!match("[^'\\\\\b\n\r\t]"))
            throw new ParseException("Illegal Character", chars.index);
        if (!match("'"))
            throw new ParseException("Unterminated char", chars.index);
        return chars.emit(Token.Type.CHARACTER);
        //maybe distinguish illegal character and missing character but im too lazy.
    }

    public Token lexString() {
        match("\"");
        while (!peek("\"")) {
            if (peek("[\n\r]"))
                throw new ParseException("Unterminated string1", chars.index);
            if (peek("\\\\")) {
                if (!match("\\\\", "[bnrt'\"\\\\]")) {
                    throw new ParseException("Invalid escape sequence", chars.index + 1);
                } else {
                    continue;
                }
            }
            if (peek("[^\"\n\r\\\\]")) {
                match("[^\"\n\r\\\\]");
            } else {
                throw new ParseException("Invalid string", chars.index);
            }
        }
        if (!match("\"")) {
            throw new ParseException("Unterminated string", chars.index);
        }
        return chars.emit(Token.Type.STRING);
    }

    public Token lexOperator() {
        if (match("[<>!=]", "=") ||
                match("&", "&") ||
                match("\\|", "\\|") ||
                match("[^\b\n\r\t]"))
            return chars.emit(Token.Type.OPERATOR);
        else
            throw new ParseException("Blank Operator", chars.index);
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!chars.has(i) ||
                    !String.valueOf(chars.get(i)).matches(patterns[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        boolean peek = peek(patterns);

        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                chars.advance();
            }
        }
        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}

