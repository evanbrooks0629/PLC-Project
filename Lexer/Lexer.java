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
            if (peek("[ \b\n\r\t]")) {
                chars.advance();
            } else {
                tokens.add(lexToken());
            }
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
        if (peek("[A-Za-z_]"))
            return lexIdentifier();
        else if (peek("[+\\-]", "[0-9]") || peek("[0-9]"))
            return lexNumber();
        else if (peek("'"))
            return lexCharacter();
        else if (peek("\""))
            return lexString();
        else
            return lexOperator();
    }

    public Token lexIdentifier() {
        while (match("@") || match("[A-Za-z0-9]")) {
            match("[A-Za-z0-9]*");

        }
        return chars.emit(Token.Type.IDENTIFIER);
    }

    public Token lexNumber() {
        if (peek("[-]")) {
            match("[-]");
        }

        if (peek("0")) {
            match("0");
            if (peek("\\d")) {
                throw new ParseException("Invalid number, leading zeros are not allowed", chars.index);
            }
        } else if (peek("[1-9]")) {
            match("[1-9]");
            while (peek("\\d")) {
                match("\\d");
            }
        } else {
            throw new ParseException("Invalid number", chars.index);
        }

        if (peek("[.]")) {
            match("[.]");
            if (!peek("\\d")) {
                throw new ParseException("Invalid decimal number, missing digit after decimal point", chars.index);
            }
            while (peek("\\d")) {
                match("\\d");
            }
            return chars.emit(Token.Type.DECIMAL);
        }

        return chars.emit(Token.Type.INTEGER);
    }

    public Token lexCharacter() {
        if (peek("'"))
            match("'");
        if (peek("\\\\"))
            lexEscape();
        else if (peek("[^'\b\n\r\t]"))
            match("[^'\b\n\r\t]");
        else {
            throw new ParseException("Illegal Character", chars.index);
        }
        if (peek("'")) {
            match("'");
            return chars.emit(Token.Type.CHARACTER);
        }
        throw new ParseException("Illegal Character", chars.index);
    }

    public Token lexString() {
        if (!match("\"")) {
            throw new ParseException("Expected opening quote for string", chars.index);
        }

        while (!peek("\"")) {
            if (peek("\\\\")) {
                match("\\\\");
                if (!match("[bnrt'\"\\\\]")) {
                    throw new ParseException("Invalid escape sequence", chars.index);
                }
            } else if (match("[^\"\n\r\\\\]")) {
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

    public void lexEscape() {
        if (peek("\\\\", "[bnrt'\"\\\\]"))
            match("\\\\", "[bnrt'\"\\\\]");
        else {
            match(".", ".");
            throw new ParseException("Illegal Escape", chars.index);
        }
    }

    public Token lexOperator() {
        if (peek("[<>!=]", "="))
            match("[<>!=]", "=");
        else
            match(".");
        return chars.emit(Token.Type.OPERATOR);
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
