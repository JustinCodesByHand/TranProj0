package Tran;
import AST.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Parser {
    TranNode tranNode;
    TokenManager tokenManager;

    public Parser(TranNode top, List<Token> tokens) {
        this.tranNode = top;
        this.tokenManager = new TokenManager(tokens);
    }

    public void Tran() throws SyntaxErrorException {
        // keep parsing while there are more tokens to process
        while (tokenManager.peek(0).isPresent()) {

            // skip over formatting tokens: NEWLINE, INDENT, DEDENT
            while (tokenManager.peek(0).isPresent()) {
                Token.TokenTypes type = tokenManager.peek(0).get().getType();
                if (type == Token.TokenTypes.NEWLINE || type == Token.TokenTypes.INDENT || type == Token.TokenTypes.DEDENT) {
                    tokenManager.matchAndRemove(type); // consume the formatting token
                } else {
                    break; // exit loop after reading real token
                }
            }

            if (!tokenManager.peek(0).isPresent()) break; // exit of no token present

            Token.TokenTypes type = tokenManager.peek(0).get().getType(); //get token type

            if (type == Token.TokenTypes.CLASS) {
                tranNode.Classes.add(parseClass()); // parse and add a class
            } else if (type == Token.TokenTypes.INTERFACE) {
                tranNode.Interfaces.add(parseInterface()); // parse and add an interface
            } else {
                // throw error if no 'class' or 'interface'
                throw new SyntaxErrorException("Expected 'class' or 'interface'",
                        tokenManager.getCurrentLine(),
                        tokenManager.getCurrentColumnNumber());
            }
        }
    }

    /// Class =  "class" IDENTIFIER ( "implements" IDENTIFIER ( "," IDENTIFIER )* )?
    ///  NEWLINE INDENT ( Constructor | MethodDeclaration | Member )* DEDENT
    private ClassNode parseClass() throws SyntaxErrorException {

        tokenManager.matchAndRemove(Token.TokenTypes.CLASS) //match and remv class token, else error
                .orElseThrow(() -> new SyntaxErrorException("Expected 'class' keyword", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

        Token nameToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD) // assume next token is class name
                .orElseThrow(() -> new SyntaxErrorException("Expected class name", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

        ClassNode node = new ClassNode();
        node.name = nameToken.getValue(); //assign name to new class node

        // optional implements token
        if (tokenManager.matchAndRemove(Token.TokenTypes.IMPLEMENTS).isPresent()) {
            do {
                Token interfaceTkn = tokenManager.matchAndRemove(Token.TokenTypes.WORD)
                        //assign interface name value
                        .orElseThrow(() ->  new SyntaxErrorException("Expected interface name after 'implements'",
                                tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));
                node.interfaces.add(interfaceTkn.getValue());
                //add interface name to node
            } while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent());
            //consume trailing commas
        }
        //newline expceted per EBNF
        tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE)
                .orElseThrow(() -> new SyntaxErrorException("Expected NEWLINE after class declaration",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));
        //expected indent
        tokenManager.matchAndRemove(Token.TokenTypes.INDENT)
                .orElseThrow(() -> new SyntaxErrorException("Expected INDENT at start of class body",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));
        //continue until dedent is reached
        while (tokenManager.peek(0).isPresent()
                && tokenManager.peek(0).get().getType() != Token.TokenTypes.DEDENT) {

            Token.TokenTypes nextType = tokenManager.peek(0).get().getType();
            //get next token type

            if (nextType == Token.TokenTypes.NEWLINE) {
                tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
                continue; //comsume possible newline token
            }

            if (nextType == Token.TokenTypes.CONSTRUCT) { //call helper method to parse constructor
                node.constructors.add(parseConstructor());
            } else if (nextType == Token.TokenTypes.SHARED || nextType == Token.TokenTypes.PRIVATE) {
                node.methods.add(parseMethodDeclaration()); // or parse method declarations
            } else if (nextType == Token.TokenTypes.WORD) {
                if (isMethodDeclarationAhead()) {  //helper method to help distiguish method declaration from member
                    node.methods.add(parseMethodDeclaration());
                } else if (isMemberDeclarationAhead()) {
                    node.members.add(parseMember());
                } else {
                    throw new SyntaxErrorException("Unrecognized declaration — not a method or member",
                            tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                }
            } else {
                throw new SyntaxErrorException("Unexpected token in class body: " + nextType,
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }
        }
            //expect dedent at end to close node
        tokenManager.matchAndRemove(Token.TokenTypes.DEDENT)
                .orElseThrow(() -> new SyntaxErrorException("Expected DEDENT at end of class body",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

        return node;
    }


    private boolean isMethodDeclarationAhead() {
        if (!tokenManager.peek(0).isPresent()) return false;

        Token.TokenTypes methodDecl = tokenManager.peek(0).get().getType();

        // shared or private
        if (methodDecl == Token.TokenTypes.SHARED || methodDecl == Token.TokenTypes.PRIVATE) {
            //expect word followed by lParen
            return tokenManager.peek(1).isPresent()
                    && tokenManager.peek(1).get().getType() == Token.TokenTypes.WORD
                    && tokenManager.peek(2).isPresent()
                    && tokenManager.peek(2).get().getType() == Token.TokenTypes.LPAREN;
        }

        // plain method: WORD followed by LPAREN
        return methodDecl == Token.TokenTypes.WORD
                && tokenManager.peek(1).isPresent()
                && tokenManager.peek(1).get().getType() == Token.TokenTypes.LPAREN;
                //returns true if matches
    }
    //retuns true if matches two consec word tokens
    private boolean isMemberDeclarationAhead() {
        return tokenManager.peek(0).isPresent()
                && tokenManager.peek(1).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.WORD
                && tokenManager.peek(1).get().getType() == Token.TokenTypes.WORD;
    }


    // parsing interface
    // Interface = "interface" IDENTIFIER NEWLINE INDENT MethodHeader* DEDENT
    private InterfaceNode parseInterface() throws SyntaxErrorException {
        Optional<Token> interfaceToken = tokenManager.matchAndRemove(Token.TokenTypes.INTERFACE);
        //ma and rm interface token
        if (interfaceToken.isEmpty()) {
            throw new SyntaxErrorException("Expected 'interface' keyword",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
            //assign and match and remove interface name
        Optional<Token> nameToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (nameToken.isEmpty()) {
            throw new SyntaxErrorException("Expected interface name",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        InterfaceNode node = new InterfaceNode();
        node.name = nameToken.get().getValue(); //create ans assign new interface node

        requireNewLine();

        //following EBNF
        Optional<Token> indentToken = tokenManager.matchAndRemove(Token.TokenTypes.INDENT);
        if (indentToken.isEmpty()) {
            throw new SyntaxErrorException("Expected INDENT after interface name", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        while (tokenManager.peek(0).isPresent()) {
            Token.TokenTypes next = tokenManager.peek(0).get().getType();

            if (next == Token.TokenTypes.NEWLINE) {
                tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE); // consume blank lines
                continue;
            }

            if (next == Token.TokenTypes.DEDENT) {
                tokenManager.matchAndRemove(Token.TokenTypes.DEDENT); // clean exit
                return node;
            }

            //if words still exist then its method header
            if (next == Token.TokenTypes.WORD) {
                MethodHeaderNode header = parseMethodHeader();
                node.methods.add(header);
            } else {
                throw new SyntaxErrorException("Unexpected token in interface body: " + next,
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }
        }

        throw new SyntaxErrorException("Expected DEDENT at end of interface", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
    }



    // parsing method header
// MethodHeader = IDENTIFIER "(" ParameterVariableDeclarations ")" (":" ParameterVariableDeclarations)? NEWLINE
    private MethodHeaderNode parseMethodHeader() throws SyntaxErrorException {
        MethodHeaderNode header = new MethodHeaderNode();

        // parse method name and check for valid identifier
        Optional<Token> nameToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (nameToken.isEmpty()) {
            throw new SyntaxErrorException("Expected method name",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        header.name = nameToken.get().getValue();

        // consume opening parenthesis for parameter list
        tokenManager.matchAndRemove(Token.TokenTypes.LPAREN);

        // check if parameters exist (type name pair)
        if (tokenManager.peek(0).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.WORD
                && tokenManager.peek(1).isPresent()
                && tokenManager.peek(1).get().getType() == Token.TokenTypes.WORD) {

            // parse first param variable declaration
            header.parameters.add(parseParameterVariableDeclaration());

            // parse additional parame separated by commas
            while (tokenManager.peek(0).isPresent()
                    && tokenManager.peek(0).get().getType() == Token.TokenTypes.COMMA) {
                tokenManager.matchAndRemove(Token.TokenTypes.COMMA);
                header.parameters.add(parseParameterVariableDeclaration());
            }
        }

        // consume closing parenthesis for param list
        tokenManager.matchAndRemove(Token.TokenTypes.RPAREN);

        // check for optional return clause starting with colon
        if (tokenManager.peek(0).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.COLON) {

            // consume colon token before return variable declarations
            tokenManager.matchAndRemove(Token.TokenTypes.COLON);

            // at least one return variable must be present
            header.returns.add(parseParameterVariableDeclaration());

            // support parsing multiple return variables
            while (tokenManager.peek(0).isPresent()
                    && tokenManager.peek(0).get().getType() == Token.TokenTypes.COMMA) {
                tokenManager.matchAndRemove(Token.TokenTypes.COMMA);
                header.returns.add(parseParameterVariableDeclaration());
            }
        }

        return header;
    }



    // parsing the variable parameter for variable declaration
// ParameterVariableDeclaration = IDENTIFIER IDENTIFIER
    private VariableDeclarationNode parseParameterVariableDeclaration() throws SyntaxErrorException {
        // try to match the type (first identifier)
        Optional<Token> typeToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (typeToken.isEmpty()) {
            throw new SyntaxErrorException("Expected parameter type",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // try to match the name (second identifier)
        Optional<Token> nameToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (nameToken.isEmpty()) {
            throw new SyntaxErrorException("Expected parameter name",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // build and return node
        VariableDeclarationNode node = new VariableDeclarationNode();
        node.type = typeToken.get().getValue();
        node.name = nameToken.get().getValue();
        node.initializer = Optional.empty(); // default for param
        return node;
    }


    //  Block = INDENT Statement* DEDENT (returns a List<StatementNode>)
    //plural for several statements
    private List<StatementNode>  parseStatements() throws SyntaxErrorException {

        Optional<Token> indentToken = tokenManager.matchAndRemove(Token.TokenTypes.INDENT);
        if (indentToken.isEmpty()) {
            throw new SyntaxErrorException("expected indent at the start of block",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        List<StatementNode> statements = new ArrayList<>();
        while(!tokenManager.done() && tokenManager.peek(0).isPresent()
        && tokenManager.peek(0).get().getType() != Token.TokenTypes.DEDENT){
            // while not empty and token not a dedent
            statements.add(parseStatement());
        }
        //require dedent at end
        Optional<Token> dedent = tokenManager.matchAndRemove(Token.TokenTypes.DEDENT);
        if (dedent.isEmpty()) {
            throw new SyntaxErrorException("expected dedent at end of block",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        return statements;
    }
    // looks for if and loop, and disambiguates
    private StatementNode parseStatement() throws SyntaxErrorException {
        // try disambiguation first, method call or assignment-like pattern
        Optional<StatementNode> disambiguated = disambiguate();
        if (disambiguated.isPresent()) {
            return disambiguated.get();
        }

        // peek at the next token, throw error if nothing is there
        Token token = tokenManager.peek(0).orElseThrow(() ->
                new SyntaxErrorException("Unexpected end of input in statement",
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

        // check for if-statement
        if (token.getType() == Token.TokenTypes.IF) {
            return parseIf();
        }
        // check for loop-statement
        else if (token.getType() == Token.TokenTypes.LOOP) {
            return parseLoop();
        }
        // otherwise, throw unexpected token error
        else {
            throw new SyntaxErrorException("Unexpected token in statement: " + token.getValue(),
                    token.getLineNumber(), token.getColumnNumber());
        }
    }



    //Parsing if statment
//If = "if" BoolExpTerm NEWLINE Block ("else" NEWLINE (Statement | Block))?
    private IfNode parseIf() throws SyntaxErrorException {
        IfNode ifNode = new IfNode();

        // mat and rem if keyword
        tokenManager.matchAndRemove(Token.TokenTypes.IF);

        // parse the condition expression
        ifNode.condition = parseBoolExpTerm(); // returns ExpressionNode

        // match and remov newline and indent to enter block
        tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
        tokenManager.matchAndRemove(Token.TokenTypes.INDENT);

        // initialize list before adding statements
        ifNode.statements = new ArrayList<>();

        // parse all statements in block until dedent
        while (tokenManager.peek(0).get().getType() != Token.TokenTypes.DEDENT) {
            ifNode.statements.add(parseStatement());
        }

        // match dedent to close block
        tokenManager.matchAndRemove(Token.TokenTypes.DEDENT);

        // optional else block
        if (tokenManager.peek(0).get().getType() == Token.TokenTypes.ELSE) {
            // match to enter else block
            tokenManager.matchAndRemove(Token.TokenTypes.ELSE);
            tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
            tokenManager.matchAndRemove(Token.TokenTypes.INDENT);

            ElseNode elseNode = new ElseNode();
            elseNode.statements = new ArrayList<>(); //build node

            // parse statements in else block until dedent
            while (tokenManager.peek(0).get().getType() != Token.TokenTypes.DEDENT) {
                elseNode.statements.add(parseStatement());
            }

            // close else block
            tokenManager.matchAndRemove(Token.TokenTypes.DEDENT);
            ifNode.elseStatement = Optional.of(elseNode);
        } else {
            // else is optional so wrap it in an empty optional
            ifNode.elseStatement = Optional.empty();
        }

        return ifNode;
    }

    /// parsing loop statements
// loop = "loop" (variablereference "=")? boolexpterm newline block
    private LoopNode parseLoop() throws SyntaxErrorException {
        LoopNode loopNode = new LoopNode();

        // match and remove loop keyword
        tokenManager.matchAndRemove(Token.TokenTypes.LOOP);

        // check for optional assignment before condition
        Optional<Token> second = tokenManager.peek(1); // lookahead for '='
        if (second.isPresent() && second.get().getType() == Token.TokenTypes.ASSIGN) {
            // example: loop i = n < 100

            // match and remove loop variable
            Token varToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get();

            // match and remove '='
            tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN);

            // create variable reference
            VariableReferenceNode varRef = new VariableReferenceNode();
            varRef.name = varToken.getValue(); // build node
            loopNode.assignment = Optional.of(varRef);

            // parse condition after assignment
            loopNode.expression = parseBoolExpTerm();
        } else {
            // example: loop n < 100

            // no assignment present
            loopNode.assignment = Optional.empty();

            // parse condition directly
            loopNode.expression = parseBoolExpTerm(); //helper method
        }

        // match newline and enter loop block
        tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
        tokenManager.matchAndRemove(Token.TokenTypes.INDENT);

        // parsestatements inside loop body
        loopNode.statements = new ArrayList<>();
        while (tokenManager.peek(0).get().getType() != Token.TokenTypes.DEDENT) {
            loopNode.statements.add(parseStatement());
        }

        // close loop block
        tokenManager.matchAndRemove(Token.TokenTypes.DEDENT);
        return loopNode;
    }

    // called by parseboolexpterm()
    //BoolExpTerm = MethodCallExpression | (Expression ( "==" | "!=" | "<=" | ">=" | ">" | "<" ) Expression) | VariableReference
    private ExpressionNode parseBoolExpFactor() throws SyntaxErrorException {
        // check if there's a next token
        Optional<Token> first = tokenManager.peek(0);
        if (first.isEmpty()) {
            throw new SyntaxErrorException("expected boolean expression", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // handle tokens that start with a word
        if (first.get().getType() == Token.TokenTypes.WORD) {
            String value = first.get().getValue(); //assign valve

            // handle literal booleans: true or false
            if (value.equals("true") || value.equals("false")) {
                tokenManager.matchAndRemove(Token.TokenTypes.WORD);
                return new BooleanLiteralNode(Boolean.parseBoolean(value));
            }

            // could be a variable or start of a comparison
            tokenManager.matchAndRemove(Token.TokenTypes.WORD);
            VariableReferenceNode left = new VariableReferenceNode();
            left.name = value; //build node

            // check if followed by comparison operator
            Optional<Token> next = tokenManager.peek(0);
            if (next.isPresent() && isComparisonOperator(next.get().getType())) {
                Token.TokenTypes opType = next.get().getType();
                tokenManager.matchAndRemove(opType); // consume the operator

                // parse the right side of the comparison
                ExpressionNode right;
                Optional<Token> rightTok = tokenManager.peek(0);
                if (rightTok.isPresent()) {
                    //if next is a number call numliteral
                    if (rightTok.get().getType() == Token.TokenTypes.NUMBER) {
                        int val = Integer.parseInt(rightTok.get().getValue());
                        tokenManager.matchAndRemove(Token.TokenTypes.NUMBER);

                        NumericLiteralNode numNode = new NumericLiteralNode();
                        numNode.value = val;
                        right = numNode;
                    } else if //word, call var ref
                    (rightTok.get().getType() == Token.TokenTypes.WORD) {
                        tokenManager.matchAndRemove(Token.TokenTypes.WORD);

                        VariableReferenceNode rightVar = new VariableReferenceNode();
                        rightVar.name = rightTok.get().getValue();
                        right = rightVar;
                    } else {
                        throw new SyntaxErrorException("invalid right-hand expression in comparison",
                                tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                    }
                } else {
                    throw new SyntaxErrorException("expected expression after comparison operator",
                            tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                }

                // build and return compare node
                CompareNode compareNode = new CompareNode();
                compareNode.left = left;
                compareNode.right = right;
                compareNode.op = mapTokenToCompareOperation(opType);
                return compareNode;
            }

            // if not a comparison, return the variable reference
            return left;
        }

        throw new SyntaxErrorException("invalid boolean expression factor", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
    }

    // left side operator right side
    /// boolexpterm = methodcallexpression | (expression ( "==" | "!=" | "<=" | ">=" | ">" | "<" ) expression) | variablereference
    private ExpressionNode parseBoolExpTerm() throws SyntaxErrorException {
        // parse the left side as a factor
        ExpressionNode left = parseBoolExpFactor();

        // if the next token is a comparison operator, we parse a comparison node
        Optional<Token> opTok = tokenManager.peek(0);
        if (opTok.isPresent() && isComparisonOperator(opTok.get().getType())) {
            Token.TokenTypes opType = opTok.get().getType();
            tokenManager.matchAndRemove(opType); // consume the operator

            ExpressionNode right;

            // peek at the rightside token
            Optional<Token> rightTok = tokenManager.peek(0);
            if (rightTok.isEmpty()) {
                throw new SyntaxErrorException("expected right-hand expression", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            Token rt = rightTok.get();
            // right side is a number
            if (rt.getType() == Token.TokenTypes.NUMBER) {
                int val = Integer.parseInt(rt.getValue());
                tokenManager.matchAndRemove(Token.TokenTypes.NUMBER);
                NumericLiteralNode numNode = new NumericLiteralNode();
                numNode.value = val;
                right = numNode;
            }
            // right side is a variable reference
            else if (rt.getType() == Token.TokenTypes.WORD) {
                tokenManager.matchAndRemove(Token.TokenTypes.WORD);
                VariableReferenceNode rightVar = new VariableReferenceNode();
                rightVar.name = rt.getValue();
                right = rightVar;
            }
            // anything else is invalid
            else {
                throw new SyntaxErrorException("invalid right-hand side in comparison", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
            }

            // build the compare node and return it
            CompareNode.CompareOperations op = mapTokenToCompareOperation(opType);
            CompareNode compare = new CompareNode();
            compare.left = left;
            compare.op = op;
            compare.right = right;
            return compare;
        }

        // if no comparison operator, return just the left expression
        return left;
    }


    //checks if type = a token type
    private boolean isComparisonOperator(Token.TokenTypes type) {
        return type == Token.TokenTypes.EQUAL || type == Token.TokenTypes.NOTEQUAL ||
                type == Token.TokenTypes.LESSTHAN || type == Token.TokenTypes.LESSTHANEQUAL ||
                type == Token.TokenTypes.GREATERTHAN || type == Token.TokenTypes.GREATERTHANEQUAL;
    }
    //take token type and outputs compareNode parameters
    private CompareNode.CompareOperations mapTokenToCompareOperation(Token.TokenTypes type) {
        return switch (type) {
            case LESSTHAN -> CompareNode.CompareOperations.lt;
            case LESSTHANEQUAL -> CompareNode.CompareOperations.le;
            case GREATERTHAN -> CompareNode.CompareOperations.gt;
            case GREATERTHANEQUAL -> CompareNode.CompareOperations.ge;
            case EQUAL -> CompareNode.CompareOperations.eq;
            case NOTEQUAL -> CompareNode.CompareOperations.ne;
            default -> throw new IllegalArgumentException("Unexpected value: " + type);
        };
    }
    private VariableReferenceNode VariableReference() throws SyntaxErrorException {
        Optional<Token> token = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (token.isEmpty()) {
            throw new SyntaxErrorException("Expected a variable reference",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        VariableReferenceNode varRef = new VariableReferenceNode();
        varRef.name = token.get().getValue();  // store variable name

        return varRef;
    }



    private Optional<StatementNode> disambiguate() throws SyntaxErrorException {
        Optional<Token> next = tokenManager.peek(0);
        Optional<Token> after = tokenManager.peek(1);


        if (next.isPresent() && after.isPresent()) {
            Token curr = next.get();
            Token following = after.get();

            // check for object.method() call like `t.add()`
            if (curr.getType() == Token.TokenTypes.WORD &&
                    tokenManager.peek(1).isPresent() && tokenManager.peek(1).get().getType() == Token.TokenTypes.DOT &&
                    tokenManager.peek(2).isPresent() && tokenManager.peek(2).get().getType() == Token.TokenTypes.WORD &&
                    tokenManager.peek(3).isPresent() && tokenManager.peek(3).get().getType() == Token.TokenTypes.LPAREN) {

                Token objectToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get();
                tokenManager.matchAndRemove(Token.TokenTypes.DOT);
                Token methodToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get();
                tokenManager.matchAndRemove(Token.TokenTypes.LPAREN);

                List<ExpressionNode> arguments = parseArguments();

                tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).orElseThrow(() ->
                        new SyntaxErrorException("Expected ')' after method call arguments",
                                tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

                MethodCallStatementNode node = new MethodCallStatementNode();
                node.methodName = methodToken.getValue();
                node.objectName = Optional.of(objectToken.getValue());
                node.parameters = arguments;

                requireNewLine();
                return Optional.of(node);
            }

            //  simple method call like hello()
            if (curr.getType() == Token.TokenTypes.WORD &&
                    following.getType() == Token.TokenTypes.LPAREN) {
                return parseMethodCallStatement();
            }
/// TODO fix
            // assignment or multi-return from method
            if (curr.getType() == Token.TokenTypes.WORD) {
                List<VariableReferenceNode> returnValues = new ArrayList<>();

                while (true) {
                    Optional<Token> peekedVar = tokenManager.peek(0);
                    if (peekedVar.isEmpty() || peekedVar.get().getType() != Token.TokenTypes.WORD) {
                        throw new SyntaxErrorException("Expected variable name", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                    }

                    Token varToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get();
                    VariableReferenceNode varRef = new VariableReferenceNode();
                    varRef.name = varToken.getValue();
                    returnValues.add(varRef);

                    Optional<Token> peekNext = tokenManager.peek(0);
                    if (peekNext.isPresent() && peekNext.get().getType() == Token.TokenTypes.COMMA) {
                        tokenManager.matchAndRemove(Token.TokenTypes.COMMA);
                    } else {
                        break;
                    }
                }

                tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN);

                Optional<Token> afterEqual = tokenManager.peek(0);
                Optional<Token> afterThat = tokenManager.peek(1);

                // RHS is a method call
                if (afterEqual.isPresent() && afterEqual.get().getType() == Token.TokenTypes.WORD &&
                        afterThat.isPresent() && afterThat.get().getType() == Token.TokenTypes.LPAREN) {

                    Optional<MethodCallExpressionNode> methodCall = parseMethodCallExpression();
                    if (methodCall.isEmpty()) {
                        throw new SyntaxErrorException("Expected method call after '='", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                    }

                    MethodCallStatementNode stmt = new MethodCallStatementNode();
                    stmt.methodName = methodCall.get().methodName;
                    stmt.parameters = methodCall.get().parameters;
                    stmt.returnValues = returnValues;

                    requireNewLine();
                    return Optional.of(stmt);
                } else {
                    // RHS is an expression
                    if (returnValues.size() != 1) {
                        throw new SyntaxErrorException("Multiple return variables only allowed for method calls", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                    }

                    ExpressionNode expr = parseExpression();

                    AssignmentNode assignment = new AssignmentNode();
                    assignment.target = returnValues.getFirst();
                    assignment.expression = expr;

                    requireNewLine();
                    return Optional.of(assignment);
                }
            }
        }

        return Optional.empty();
    }


    // parses a method call as a statement
    private Optional<StatementNode> parseMethodCallStatement() throws SyntaxErrorException {
        // try to match the method name
        Optional<Token> methodToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        if (methodToken.isEmpty()) {
            return Optional.empty();
        }
        //assign value
        String methodName = methodToken.get().getValue();

        // match opening parenthesis
        Optional<Token> openParen = tokenManager.matchAndRemove(Token.TokenTypes.LPAREN);
        if (openParen.isEmpty()) {
            throw new SyntaxErrorException("expected '(' after method name",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // parse arguments (can be empty)
        List<ExpressionNode> arguments = parseArguments();

        // match closing parenthesis
        Optional<Token> closeParen = tokenManager.matchAndRemove(Token.TokenTypes.RPAREN);
        if (closeParen.isEmpty()) {
            throw new SyntaxErrorException("expected ')' after arguments",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // build method call node
        MethodCallStatementNode node = new MethodCallStatementNode();
        node.methodName = methodName;
        node.parameters = arguments;
        return Optional.of(node);
    }


    // parses method call arguments into a list of expressions
    private List<ExpressionNode> parseArguments() throws SyntaxErrorException {
        List<ExpressionNode> args = new ArrayList<>();

        // check for empty argument list
        if (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() == Token.TokenTypes.RPAREN) {
            return args;
        }

        // parse the first argument
        args.add(parseExpression());

        // parse additional comma-separated arguments
        while (tokenManager.peek(0).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.COMMA) {
            tokenManager.matchAndRemove(Token.TokenTypes.COMMA);
            args.add(parseExpression());
        }

        return args;
    }


    // parses an assignment statement
    private AssignmentNode parseAssignment(VariableReferenceNode target) throws SyntaxErrorException {
        // expect and consume '='
        Optional<Token> assignToken = tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN);
        if (assignToken.isEmpty()) {
            throw new SyntaxErrorException("Expected '=' in assignment",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // parse the expression to assign
        ExpressionNode expression = parseExpression();

        // make sure the expression is followed by a newline or dedent
        if (tokenManager.peek(0).isPresent() &&
                tokenManager.peek(0).get().getType() != Token.TokenTypes.NEWLINE ||
                tokenManager.peek(0).get().getType() != Token.TokenTypes.DEDENT) {
            throw new SyntaxErrorException("Expected NEWLINE after assignment expression",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        requireNewLine();

        // build and return the assignment node
        AssignmentNode assignment = new AssignmentNode();
        assignment.target = target;
        assignment.expression = expression;
        return assignment;
    }

    // helper method called from parseAssignment to differ method call from varRef
 //   private ExpressionNode parseExpression() throws SyntaxErrorException {
        // Check for method call first
   //     Optional<MethodCallExpressionNode> methodCall = parseMethodCallExpression();
     //   if (methodCall.isPresent()) {
       //     return methodCall.get();
      //  }
        // default to variable reference
      //  return parseVariableReference();
 //   }
    ///MethodCallExpression =  (IDENTIFIER ".")? IDENTIFIER "(" (Expression ("," Expression )* )? ")"
    private Optional<MethodCallExpressionNode> parseMethodCallExpression() throws SyntaxErrorException {
        // check for optional object name  "obj." in "obj.method()"
        Optional<String> objectName = Optional.empty();
        if (tokenManager.peek(1).isPresent()
                && tokenManager.peek(1).get().getType() == Token.TokenTypes.DOT) {
                //looks ahead for the dot
            objectName = Optional.of(tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue());
            tokenManager.matchAndRemove(Token.TokenTypes.DOT); //optional since may/maynot exist
        }

        // parse method name only if followed by "("
        if (tokenManager.peek(0).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.WORD
                && tokenManager.peek(1).isPresent()
                && tokenManager.peek(1).get().getType() == Token.TokenTypes.LPAREN) {
            // only assign method name if these conditions are met
            String methodName = tokenManager.matchAndRemove(Token.TokenTypes.WORD).get().getValue();

            tokenManager.matchAndRemove(Token.TokenTypes.LPAREN); //now safe to consume (

            MethodCallExpressionNode node = new MethodCallExpressionNode();
            node.objectName = objectName; // assign params to node
            node.methodName = methodName;

            // parse parameters (if any)
            while (!tokenManager.done() && tokenManager.peek(0).isPresent()
                    && tokenManager.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
                //parse params until rparen is reached
                node.parameters.add(parseExpression());

                if (tokenManager.peek(0).isPresent()
                        && tokenManager.peek(0).get().getType() == Token.TokenTypes.COMMA) {
                    tokenManager.matchAndRemove(Token.TokenTypes.COMMA);
                    //consume comma and restart loop
                }
            }

            tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).orElseThrow(() -> new SyntaxErrorException("Expected ')'",
                            tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

            return Optional.of(node);
            //optional to avoid errors
        }

        return Optional.empty(); // not a method call
    }
    /// VariableReference = IDENTIFIER
    private VariableReferenceNode parseVariableReference() throws SyntaxErrorException {
        Optional<Token> token = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
        //confirm parsing word token
        if (token.isEmpty()) {
            throw new SyntaxErrorException("Expected variable name",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        //assign var ref name from word token
        VariableReferenceNode varRef = new VariableReferenceNode();
        varRef.name = token.get().getValue(); // Set name from token
        return varRef;
    }


    // constructor = "construct" "(" [ParameterVariableDeclarations] ")" NEWLINE [Block]
    private ConstructorNode parseConstructor() throws SyntaxErrorException {
        tokenManager.matchAndRemove(Token.TokenTypes.CONSTRUCT);

        List<VariableDeclarationNode> parameters = new ArrayList<>();

        // match ( and validate
        Optional<Token> openParen = tokenManager.matchAndRemove(Token.TokenTypes.LPAREN);
        if (openParen.isEmpty()) {
            throw new SyntaxErrorException("Expected '(' in constructor",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        // parse parameters if present
        if (tokenManager.peek(0).isPresent() &&
                tokenManager.peek(0).get().getType() != Token.TokenTypes.RPAREN) {
            //rparen stops
            parameters.add(parseParameterVariableDeclaration());
            while (tokenManager.matchAndRemove(Token.TokenTypes.COMMA).isPresent()) {
                parameters.add(parseParameterVariableDeclaration());
                //comma adds more variables
            }
        }

        // match ')' and validate
        Optional<Token> closeParen = tokenManager.matchAndRemove(Token.TokenTypes.RPAREN);
        if (closeParen.isEmpty()) {
            throw new SyntaxErrorException("Expected ')' after constructor parameters",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        requireNewLine();

        List<VariableDeclarationNode> locals = new ArrayList<>();
        List<StatementNode> body = new ArrayList<>();

        // optional block (body)
        if (tokenManager.peek(0).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.INDENT) {
            body = parseMethodBody(locals); // properly consumes indent and dedent
        }
        //add and return node
        ConstructorNode constructor = new ConstructorNode();
        constructor.parameters = parameters;
        constructor.locals = locals;
        constructor.statements = body;
        return constructor;
    }



    // parses a method declaration
// methoddeclaration = "private"? "shared"? methodheader newline methodbody
    private MethodDeclarationNode parseMethodDeclaration() throws SyntaxErrorException {
        System.out.println("parseMethodDeclaration: entering at line " + tokenManager.getCurrentLine());

        boolean isPrivate = false;
        boolean isShared = false;

        // handle optional access modifiers
        // handle optional access modifiers in any order
        while (tokenManager.peek(0).isPresent()) {
            Token.TokenTypes nextType = tokenManager.peek(0).get().getType();
            if (nextType == Token.TokenTypes.PRIVATE) {
                tokenManager.matchAndRemove(Token.TokenTypes.PRIVATE);
                isPrivate = true;
            } else if (nextType == Token.TokenTypes.SHARED) {
                tokenManager.matchAndRemove(Token.TokenTypes.SHARED);
                isShared = true;
            } else {
                break;
            }
        }

        // parse method header
        MethodHeaderNode header = parseMethodHeader();
        System.out.println("parseMethodDeclaration: Parsed method '" + header.name + "'");

        // match newline after header
        if (tokenManager.peek(0).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.NEWLINE) {
            tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
            System.out.println("parseMethodDeclaration: Consumed NEWLINE after header");
        }

        // prepare locals and body
        List<VariableDeclarationNode> locals = new ArrayList<>();
        List<StatementNode> body = new ArrayList<>();

        // parse method body if indent is present
        if (tokenManager.peek(0).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.INDENT) {
            body = parseMethodBody(locals);
        }

        // add and return method node
        MethodDeclarationNode methodNode = new MethodDeclarationNode();
        methodNode.name = header.name;
        methodNode.parameters = header.parameters;
        methodNode.returns = header.returns;
        methodNode.locals = locals;
        methodNode.statements = body;
        methodNode.isPrivate = isPrivate;
        methodNode.isShared = isShared;

        return methodNode;
    }




    // parses method body: first variable declarations, then statements
// methodbody = indent ( variabledeclarations )*  statement* dedent
    private List<StatementNode> parseMethodBody(List<VariableDeclarationNode> locals) throws SyntaxErrorException {

        // require indent
        Optional<Token> indentToken = tokenManager.matchAndRemove(Token.TokenTypes.INDENT);
        if (indentToken.isEmpty()) {
            throw new SyntaxErrorException("Expected INDENT at start of method body",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        List<StatementNode> statements = new ArrayList<>();

        // parse local variable declarations
        while (tokenManager.peek(0).isPresent()
                && tokenManager.peek(1).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.WORD
                && tokenManager.peek(1).get().getType() == Token.TokenTypes.WORD) {

            VariableDeclarationNode varDeclaration = parseParameterVariableDeclaration();
            locals.add(varDeclaration);

            // check if the local has an initializer
            if (tokenManager.peek(0).isPresent()
                    && tokenManager.peek(0).get().getType() == Token.TokenTypes.ASSIGN) {
                tokenManager.matchAndRemove(Token.TokenTypes.ASSIGN);
                varDeclaration.initializer = Optional.of(parseExpression());
            }

            // newline after local declaration
            requireNewLine();
        }

        // skip any newlines between locals and statements
        while (tokenManager.peek(0).isPresent()
                && tokenManager.peek(0).get().getType() == Token.TokenTypes.NEWLINE) {
            tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
        }

        // parse statements
        while (tokenManager.peek(0).isPresent()) {
            Token.TokenTypes type = tokenManager.peek(0).get().getType();

            // end method body if dedent
            if (type == Token.TokenTypes.DEDENT) {
                break;
            }

            // skip trailing newlines
            if (type == Token.TokenTypes.NEWLINE) {
                tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE);
                continue;
            }

            // parse and store statement
            StatementNode stmt = parseStatement();
            if (stmt != null) {
                statements.add(stmt);
            }
        }

        // final dedent
        Optional<Token> dedentToken = tokenManager.matchAndRemove(Token.TokenTypes.DEDENT);
        if (dedentToken.isEmpty()) {
            throw new SyntaxErrorException("Expected DEDENT at end of method body",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        return statements;
    }


    // Member = VariableDeclarations  (helper method)
    private MemberNode parseMember() throws SyntaxErrorException {
        VariableDeclarationNode decl = parseParameterVariableDeclaration();
        MemberNode member = new MemberNode();
        member.declaration = decl;
        return member;
    }

    // Helper method to ensure a newline exists
    private void requireNewLine() throws SyntaxErrorException {
        Optional<Token> newlineToken = tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE) ;

         if (newlineToken.isPresent() ){
            // while loop for several nelines or dedents
            while(tokenManager.matchAndRemove(Token.TokenTypes.NEWLINE).isPresent()){
                // eats newline or dedent until neither exists
            }
        }
        else if(tokenManager.peek(0).get().getType() == Token.TokenTypes.DEDENT){
        }
        else if (!newlineToken.isPresent()) {
            throw new SyntaxErrorException("Expected NEWLINE",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }
        else {
           throw new SyntaxErrorException("Expected DEDENT",
            tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());   }
    }


    // parses an expression following: expression = term { ("+" | "-") term }
    private ExpressionNode parseExpression() throws SyntaxErrorException {
        ExpressionNode left = parseTerm(); //helper method

        while (tokenManager.peek(0).isPresent()) {
            Token.TokenTypes type = tokenManager.peek(0).get().getType();

            // only proceed if a + or -
            if (type != Token.TokenTypes.PLUS && type != Token.TokenTypes.MINUS) {
                break;
            }

            // match and remove the op token
            tokenManager.matchAndRemove(Token.TokenTypes.PLUS);
            tokenManager.matchAndRemove(Token.TokenTypes.MINUS);

            // parse the right-hand side of expression
            ExpressionNode right = parseTerm();

            // build the math node
            MathOpNode math = new MathOpNode();
            math.left = left;
            math.right = right;
            math.op = (type == Token.TokenTypes.PLUS) ?
                    MathOpNode.MathOperations.add :
                    MathOpNode.MathOperations.subtract;

            // maintain left side expression tree
            left = math;
        }

        return left;
    }


    // parses a term following: term = factor { ("*" | "/" | "%") factor }
    private ExpressionNode parseTerm() throws SyntaxErrorException {
        ExpressionNode left = parseFactor();

        while (tokenManager.peek(0).isPresent()) {
            Token.TokenTypes type = tokenManager.peek(0).get().getType();

            // stop when hitting delimiters
            if (type == Token.TokenTypes.COMMA ||
                    type == Token.TokenTypes.RPAREN ||
                    type == Token.TokenTypes.NEWLINE) {
                break;
            }

            // handle *, /, %
            if (type == Token.TokenTypes.TIMES || type == Token.TokenTypes.DIVIDE || type == Token.TokenTypes.MODULO) {
                Token op = tokenManager.matchAndRemove(type).get();

                // parse right-hand side
                ExpressionNode right = parseFactor();

                // create math op node
                MathOpNode math = new MathOpNode();
                math.left = left;
                math.right = right;

                if (type == Token.TokenTypes.TIMES) {
                    math.op = MathOpNode.MathOperations.multiply;
                } else if (type == Token.TokenTypes.DIVIDE) {
                    math.op = MathOpNode.MathOperations.divide;
                } else { // modulo
                    math.op = MathOpNode.MathOperations.modulo;
                }

                // update left for  expression
                left = math;
            } else {
                break;
            }
        }

        return left;
    }

// factor = number | variablereference | stringliteral | characterliteral | methodcallexpression
//         | "(" expression ")" | "new" identifier "(" (expression ("," expression)*)? ")"
    private ExpressionNode parseFactor() throws SyntaxErrorException {




        if (tokenManager.peek(0).isEmpty()) {
            throw new SyntaxErrorException("expected expression", tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }

        Token token = tokenManager.peek(0).get();

        if (token.getType() == Token.TokenTypes.DOT) {
            throw new SyntaxErrorException("Unexpected DOT at start of factor — likely missing left-hand side",
                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
        }


        switch (token.getType()) {
            case NUMBER: {
                // number literal
                tokenManager.matchAndRemove(Token.TokenTypes.NUMBER);
                NumericLiteralNode numberNode = new NumericLiteralNode();
                numberNode.value = Float.parseFloat(token.getValue());
                return numberNode;
            }

            case QUOTEDSTRING: {
                // string literal
                tokenManager.matchAndRemove(token.getType());
                StringLiteralNode stringNode = new StringLiteralNode();
                stringNode.value = token.getValue();
                return stringNode;
            }

            case WORD: {

                ExpressionNode base = parseVariableReference();

                // Handle chained method calls (object.method())
                while (tokenManager.peek(0).isPresent() && tokenManager.peek(0).get().getType() == Token.TokenTypes.DOT) {
                    tokenManager.matchAndRemove(Token.TokenTypes.DOT);

                    Token methodToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD).orElseThrow(() ->
                            new SyntaxErrorException("Expected method name after '.'",
                                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

                    tokenManager.matchAndRemove(Token.TokenTypes.LPAREN).orElseThrow(() ->
                            new SyntaxErrorException("Expected '(' after method name",
                                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

                    List<ExpressionNode> args = parseArguments();

                    tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).orElseThrow(() ->
                            new SyntaxErrorException("Expected ')' after arguments",
                                    tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

                    MethodCallExpressionNode call = new MethodCallExpressionNode();
                    call.methodName = methodToken.getValue();
                    call.parameters = args;

                    if (base instanceof VariableReferenceNode varRef) {
                        call.objectName = Optional.of(varRef.name);
                    } else {
                        throw new SyntaxErrorException("Expected variable before '.' in method call chain",
                                tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                    }

                    base = call;
                }

                return base;
            }





            case LPAREN: {
                // grouped expression
                tokenManager.matchAndRemove(Token.TokenTypes.LPAREN);
                ExpressionNode inner = parseExpression();
                if (tokenManager.matchAndRemove(Token.TokenTypes.RPAREN).isEmpty()) {
                    throw new SyntaxErrorException("expected ')' after grouped expression",
                            tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                }
                return inner;
            }

            case NEW: {
                // object instantiation
                tokenManager.matchAndRemove(Token.TokenTypes.NEW);

                Optional<Token> classToken = tokenManager.matchAndRemove(Token.TokenTypes.WORD);
                if (classToken.isEmpty()) {
                    throw new SyntaxErrorException("expected class name after 'new'",
                            tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());
                }

                NewNode newNode = new NewNode();
                newNode.className = classToken.get().getValue();

                // parse constructor arguments
                tokenManager.matchAndRemove(Token.TokenTypes.LPAREN)
                        .orElseThrow(() -> new SyntaxErrorException("expected '(' after class name",
                                tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

                if (tokenManager.peek(0).isPresent() &&
                        tokenManager.peek(0).get().getType() != Token.TokenTypes.RPAREN) {

                    newNode.parameters.add(parseExpression());

                    while (tokenManager.peek(0).isPresent() &&
                            tokenManager.peek(0).get().getType() == Token.TokenTypes.COMMA) {
                        tokenManager.matchAndRemove(Token.TokenTypes.COMMA);
                        newNode.parameters.add(parseExpression());
                    }
                }
                //use rparen to stop
                tokenManager.matchAndRemove(Token.TokenTypes.RPAREN)
                        .orElseThrow(() -> new SyntaxErrorException("expected ')' after arguments",
                                tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber()));

                return newNode;
            }

            default:
                System.out.println("DEBUG: Unexpected token in factor: " + token.getType());
                throw new SyntaxErrorException("Unexpected token in expression: " + token.getType(),
                        tokenManager.getCurrentLine(), tokenManager.getCurrentColumnNumber());

        }
    }
}

