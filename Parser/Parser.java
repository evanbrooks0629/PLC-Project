package plc.project;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }


    private void checkKeyword(String expectedToken) {
        if (match(expectedToken))
            return;
        if (tokens.has(0))
            throw new ParseException("Expected " + expectedToken + " INDEX:" + getIndex(true), getIndex(true));
        else
            throw new ParseException("Expected " + expectedToken + " INDEX:" + getIndex(false), getIndex(false));
    }

    private void checkIdentifier() {
        if (peek(Token.Type.IDENTIFIER))
            return;
        if (tokens.has(0))
            throw new ParseException("Expected identifier INDEX:" + getIndex(true), getIndex(true));
        else
            throw new ParseException("Expected identifier INDEX:" + getIndex(false), getIndex(false));
    }
    private int getIndex(boolean atFront) {
        if (atFront)
            return tokens.get(0).getIndex();
        return tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Global> fields = new ArrayList<Ast.Global>();
        List<Ast.Function> methods = new ArrayList<Ast.Function>();

        while(peek("LIST") || peek("VAR") || peek("VAL")) {
            fields.add(parseGlobal());
        }

        while (peek("FUN")) {
            methods.add(parseFunction());
        }

        if (!tokens.has(0))
            return new Ast.Source(fields, methods);
        else
            throw new ParseException("illegal source" + " INDEX:" + getIndex(true), getIndex(true));
    }

    /**
     * Parses the {@code global} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        Ast.Global global;

        if (peek("LIST")) {
            global = parseList();
        } else if (peek("VAR")) {
            global = parseMutable();
        } else if (peek("VAL")) {
            global = parseImmutable();
        } else {
            if (tokens.has(0))
                throw new ParseException("Expected LIST, VAR or VAL INDEX:" + getIndex(true), getIndex(true));
            else
                throw new ParseException("Expected LIST, VAR or VAL INDEX:" + getIndex(false), getIndex(false));
        }

        checkKeyword(";");

        return global;
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        match("LIST");

        checkIdentifier();
        String name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);
        checkKeyword("=");
        checkKeyword("[");

        List<Ast.Expression> expressions = new ArrayList<>();
        expressions.add(parseExpression());

        while (peek(",")) {
            match(",");
            if (peek("]")) {
                throw new ParseException("Unexpected ]" + "INDEX:" + getIndex(true), getIndex(true));
            }
            expressions.add(parseExpression());
        }

        checkKeyword("]");

        Ast.Expression.PlcList list = new Ast.Expression.PlcList(expressions);

        return new Ast.Global(name, true, Optional.of(list));
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        match("VAR");

        checkIdentifier();
        String name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);

        if (match("=")) {
            Ast.Expression statement = parseExpression();
            return new Ast.Global(name, true, Optional.of(statement));
        }
        return new Ast.Global(name, true, Optional.empty());
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        match("VAL");

        checkIdentifier();
        String name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);

        checkKeyword("=");

        Ast.Expression statement = parseExpression();

        return new Ast.Global(name, false, Optional.of(statement));
    }

    /**
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        match("FUN");

        checkIdentifier();
        String name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);
        checkKeyword("(");

        List<String> parameters = new ArrayList<>();

        while (peek(Token.Type.IDENTIFIER)) {
            parameters.add(tokens.get(0).getLiteral());
            match(Token.Type.IDENTIFIER);

            if (match(",")) {
                if (peek(")")) {
                    throw new ParseException("Unexpected )" + "INDEX:" + getIndex(true), getIndex(true));
                }
            } else {
                break;
            }
        }

        checkKeyword(")");
        checkKeyword("DO");

        List<Ast.Statement> statements = parseBlock();

        checkKeyword("END");

        return new Ast.Function(name, parameters, statements);
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block of statements.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        List<Ast.Statement> statements = new ArrayList<Ast.Statement>();
        while (!peek("END")) {
            //I'm not sure about this part should i just check the keywords or just identifier
            statements.add(parseStatement());
            if (!tokens.has(0)) {
                throw new ParseException("Block not closed at " + getIndex(false), getIndex(false));
            }
        }
        return statements;
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {

        if (peek("RETURN")) {
            return parseReturnStatement();
        } else if (peek("SWITCH")) {
            return parseSwitchStatement();
        } else if (peek("WHILE")) {
            return parseWhileStatement();
        } else if (peek("IF")) {
            return parseIfStatement();
        } else if (peek("LET")){
            return parseDeclarationStatement();
        } else {
            Ast.Expression left = parseExpression();

            if (match("=")) {
                Ast.Expression right = parseExpression();
                checkKeyword(";");
                return new Ast.Statement.Assignment(left, right);
            } else {
                checkKeyword(";");
                return new Ast.Statement.Expression(left);
            }
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        match("LET");

        if (!peek(Token.Type.IDENTIFIER)) {
            if (tokens.has(0))
                throw new ParseException("Expected identifier" + " INDEX:" + getIndex(true), getIndex(true));
            else
                throw new ParseException("Expected identifier" + " INDEX:" + getIndex(false), getIndex(false));
        }

        String name = tokens.get(0).getLiteral();
        match(Token.Type.IDENTIFIER);

        Optional<Ast.Expression> value = Optional.empty();

        if (match("=")) {
            Ast.Expression initExpression = parseExpression();
            value = Optional.of(initExpression);
        }

        checkKeyword(";");

        return new Ast.Statement.Declaration(name, value);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        match("IF");

        Ast.Expression condition = parseExpression();

        checkKeyword("DO");

        List<Ast.Statement> ifStatements = new ArrayList<>();
        List<Ast.Statement> elseStatements = new ArrayList<>();

        while (!peek("ELSE") && !peek("END"))
            ifStatements.add(parseStatement());

        if (match("ELSE"))
            elseStatements = parseBlock();

        checkKeyword("END");

        return new Ast.Statement.If(condition, ifStatements, elseStatements);
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        match("SWITCH");

        Ast.Expression condition = parseExpression();

        List<Ast.Statement.Case> cases = new ArrayList<>();

        boolean hasDefault = false;

        while (!peek("END")) {
            if (peek("CASE"))
                cases.add(parseCaseStatement());
            else if (peek("DEFAULT")) {
                hasDefault = true;
                cases.add(parseCaseStatement());
                break;
            }
        }

        if (!hasDefault)
            throw new ParseException("Expected DEFAULT." + " INDEX:" + getIndex(true), getIndex(true));

        checkKeyword("END");

        return new Ast.Statement.Switch(condition, cases);
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule. 
     * This method should only be called if the next tokens start the case or 
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        Optional<Ast.Expression> condition = Optional.empty();

        if (match("CASE")) {
            condition = Optional.of(parseExpression());
            checkKeyword(":");
        }
        else
            checkKeyword("DEFAULT");

        List<Ast.Statement> statements = new ArrayList<>();

        while (!peek("CASE") && !peek("DEFAULT") && !peek("END"))
            statements.add(parseStatement());

        return new Ast.Statement.Case(condition, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        match("WHILE");

        Ast.Expression condition = parseExpression();

        checkKeyword("DO");

        List<Ast.Statement> statements = parseBlock();

        checkKeyword("END");

        return new Ast.Statement.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        match("RETURN");

        Ast.Expression value = parseExpression();

        checkKeyword(";");

        return new Ast.Statement.Return(value);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        Ast.Expression result = parseComparisonExpression();
        while (peek("&&") || peek("||")) {
            String operator = tokens.get(0).getLiteral();
            match(operator);
            Ast.Expression right = parseComparisonExpression();
            result = new Ast.Expression.Binary(operator, result, right);
        }
        return result;
    }

    /**
     * Parses the {@code comparison-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        Ast.Expression result = parseAdditiveExpression();
        while (peek("<") || peek(">") || peek("==") || peek("!=")) {
            String operator = tokens.get(0).getLiteral();
            match(operator);
            Ast.Expression right = parseAdditiveExpression();
            result = new Ast.Expression.Binary(operator, result, right);
        }
        return result;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression result = parseMultiplicativeExpression();
        while (peek("+") || peek("-")) {
            String operator = tokens.get(0).getLiteral();
            match(operator);
            Ast.Expression right = parseMultiplicativeExpression();
            result = new Ast.Expression.Binary(operator, result, right);
        }
        return result;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression result = parsePrimaryExpression();
        while (peek("*") || peek("/") || peek("^")) {
            String operator = tokens.get(0).getLiteral();
            match(operator);
            Ast.Expression right = parsePrimaryExpression();
            result = new Ast.Expression.Binary(operator, result, right);
        }
        return result;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {
        // NIL
        if (peek("NIL")) {
            match("NIL");
            return new Ast.Expression.Literal(null);
        }

        // TRUE
        else if (peek("TRUE")) {
            match("TRUE");
            return new Ast.Expression.Literal(true);
        }

        // FALSE
        else if (peek("FALSE")) {
            match("FALSE");
            return new Ast.Expression.Literal(false);
        }

        // INTEGER
        else if (peek(Token.Type.INTEGER)) {
            BigInteger value = new BigInteger(tokens.get(0).getLiteral());
            match(Token.Type.INTEGER);
            return new Ast.Expression.Literal(value);
        }

        // DECIMAL
        else if (peek(Token.Type.DECIMAL)) {
            BigDecimal value = new BigDecimal(tokens.get(0).getLiteral());
            match(Token.Type.INTEGER);
            return new Ast.Expression.Literal(value);
        }

        // CHARACTER
        else if (peek(Token.Type.CHARACTER)) {
            String temp = tokens.get(0).getLiteral();
            temp = temp.replace("\\b", "\b");
            temp = temp.replace("\\n", "\n");
            temp = temp.replace("\\r", "\r");
            temp = temp.replace("\\t", "\t");
            temp = temp.replace("\\\"", "\"");
            temp = temp.replace("\\\\", "\\");
            temp = temp.replace("\\'", "\'");
            temp = temp.substring(1, temp.length() - 1);
            char c = temp.charAt(0);
            match(Token.Type.CHARACTER);
            return new Ast.Expression.Literal(c);
        }

        // STRING
        else if (peek(Token.Type.STRING)) {
            String temp = tokens.get(0).getLiteral();
            temp = temp.replace("\\b", "\b");
            temp = temp.replace("\\n", "\n");
            temp = temp.replace("\\r", "\r");
            temp = temp.replace("\\t", "\t");
            temp = temp.replace("\\\"", "\"");
            temp = temp.replace("\\\\", "\\");
            temp = temp.replace("\\'", "\'");
            temp = temp.substring(1, temp.length() - 1);
            match(Token.Type.STRING);
            return new Ast.Expression.Literal(temp);
        }

        // EXPRESSION [within ()]
        else if (peek("(")) {
            match("(");
            Ast.Expression.Group group = new Ast.Expression.Group(parseExpression());
            if (peek(")")) {
                match(")");
                return group;
            } else {
                if (tokens.has(0))
                    throw new ParseException("Expected )." + " INDEX:" + getIndex(true), getIndex(true));
                else
                    throw new ParseException("Expected )." + " INDEX:" + (getIndex(false)), getIndex(false));
            }
        }

        // IDENTIFIER within nested expressions
        else if (peek(Token.Type.IDENTIFIER)) {
            String name = tokens.get(0).getLiteral();
            match(Token.Type.IDENTIFIER);
            if (peek("(")) {
                match("(");
                List<Ast.Expression> arguments = new ArrayList<>();
                if (!peek(")")) { // If there's something inside the parentheses
                    do {
                        arguments.add(parseExpression()); // Parse the first/next expression
                        if (!peek(",")) break; // If there's no comma, stop parsing arguments
                        match(","); // Consume the comma to move to the next argument
                    } while (true);
                }
                if (!peek(")")) {
                    if (tokens.has(0))
                        throw new ParseException("Expected )." + " INDEX:" + getIndex(true), getIndex(true));
                    else
                        throw new ParseException("Expected )." + " INDEX:" + (getIndex(false)), getIndex(false));
                }
                match(")");
                return new Ast.Expression.Function(name, arguments);

            }

            else if (peek("[")) {
                match("[");
                Ast.Expression index = parseExpression();
                if (!peek("]")) {
                    if (tokens.has(0))
                        throw new ParseException("Expected ]." + " INDEX:" + getIndex(true), getIndex(true));
                    else
                        throw new ParseException("Expected ]." + " INDEX:" + (getIndex(false)), getIndex(false));
                }
                match("]");
                return new Ast.Expression.Access(Optional.of(index), name);
            }

            else {
                return new Ast.Expression.Access(Optional.empty(), name);
            }
        }

        if (tokens.has(0))
            throw new ParseException("Invalid pattern object: " + getIndex(true), getIndex(true));
        else
            throw new ParseException("Expected a token at " + getIndex(false), getIndex(false));
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            } else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            } else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }

        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);

        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }

        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
