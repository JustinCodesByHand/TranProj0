package Tran;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.lang.StringBuilder;


public class Lexer {
    private TextManager textManager;
    // imports text manger under var textManager

    private int verticleLineNumber;
    //holds the  vertical line number

    private int charPosition;
    //holds horizontal position of char

    //vars to help indentation
    private int previousIndentLevel;
    private int newIndentLevel;

    private int dedentCounter;
    public Lexer(String input) {
        //loads hashmap of keywords, symbols
        keyWordMap();
        symbolMap();

        //init members for every instance of class
        this.textManager = new TextManager(input); //sends the string to textManager via the "input" var
        this.verticleLineNumber = 0;
        this.charPosition = 0;
    }

    public List<Token> Lex() throws Exception {
        var tokenList = new LinkedList<Token>(); //name of linkedList of tokens

        while (!textManager.isAtEnd()) { // loops while textManager is NOT at end
            char currentCharacter = textManager.peekCharacter();
            // assigns current char by calling on the textManager class
            // switch statement that filters currentCharacter by type: words, newline, and spaces
            switch (currentCharacter) {

                case '\"':
                    tokenList.add(readQuotedString());
                    break;

                case '\n': // if char = NewLine
                    tokenList.add(new Token(Token.TokenTypes.NEWLINE, verticleLineNumber++, charPosition++));
                    if(!textManager.isAtEnd()) {
                        textManager.getCharacter();
                    }
                    charPosition = 0;
                    int tabCounter = 0;
                    int spaceCounter = 0;

                    if (!textManager.isAtEnd() && textManager.peekCharacter() == '{') {
                        commentedCode();
                        while (newIndentLevel>0){
                            newIndentLevel--;
                            previousIndentLevel = 0;
                            tokenList.add(new Token(Token.TokenTypes.DEDENT, verticleLineNumber, charPosition));
                        }
                        //TODO: examp4 bracket after a newline returns dedent
                        continue;


                    }
                    while (!textManager.isAtEnd() && ((textManager.peekCharacter() == '\t'
                            || textManager.peekCharacter() == ' '))) {

                        //whiile char is a tab or space after a \n
                        if (!textManager.isAtEnd() && textManager.peekCharacter() == '\t') {
                            tabCounter++;

                        } else {
                            spaceCounter++;
                        }


                        textManager.getCharacter();
                    }

                    if (!textManager.isAtEnd() && textManager.peekCharacter()=='\n'){
                        break;
                    }

                        List<Token> indentTokens = indentTracker(tabCounter, spaceCounter);
                        tokenList.addAll(indentTokens);
                    break;

                case ' ': //if there is an empty space
                    if(!textManager.isAtEnd()) {
                        textManager.getCharacter();
                        charPosition++;
                    }
                    //^increments position, skipping over black space
                    break;

                default: //assumes char is either a letter or a symbol

                    if (currentCharacter == '{'){
                        commentedCode();
                        continue;
                    }

                    if (Character.isAlphabetic(currentCharacter)) {
                        tokenList.add(readWord()); // if char is letter, call readWord() method
                    }
                    else if (currentCharacter == '.' && Character.isDigit(textManager.peekCharacter(1))){
                        tokenList.add(readNumber());
                    }

                    else if (currentCharacter == '-' && Character.isDigit(textManager.peekCharacter(1))){
                        textManager.getCharacter();
                        tokenList.add(new Token(Token.TokenTypes.MINUS, verticleLineNumber, charPosition));
                        tokenList.add(readNumber());
                    }

                    else if (known_Symbols.containsKey(String.valueOf(currentCharacter))) {
                        //^checks if char matches key in known_Symbols hashmap
                        tokenList.add(readSymbol()); // then calls readSymbol method
                    }
                    //TODO: reading numbers and returning the number token with string value
                    else if (Character.isDigit(currentCharacter)){
                        tokenList.add(readNumber());
                    }
                    else {
                        textManager.getCharacter(); //gets next char if these checks fail
                        charPosition++;
                    }

                    break;
                    }

            }
        //retunds dendent tokens at EOF
        while(previousIndentLevel>0){
            previousIndentLevel--;
            tokenList.add(new Token(Token.TokenTypes.DEDENT, verticleLineNumber, charPosition++));
        }

        return tokenList; //returns list of tokens from lexed input
    }
    // track and update the previousIndentationLevel
    //and helps handle spaces to determine token stuffs


    private void commentedCode() throws SyntaxErrorException {
        char currentCharacter = textManager.getCharacter();

        while (!textManager.isAtEnd() && currentCharacter != '}'){
            currentCharacter = textManager.getCharacter();
        }
        if(!textManager.isAtEnd() && textManager.peekCharacter() != '\n'){
            textManager.getCharacter();
        }
        else if(textManager.isAtEnd()){
            throw new SyntaxErrorException("unclosed comment block", verticleLineNumber, charPosition);
        }
    }


    private Token readQuotedString(){
        StringBuilder stringBuilder = new StringBuilder();
        char currentCharacter = textManager.getCharacter();
        if(currentCharacter == '"' ){
            currentCharacter = textManager.getCharacter();
            while(currentCharacter != '"') {
                stringBuilder.append(currentCharacter);
                currentCharacter = textManager.getCharacter();
            }
            return new Token(Token.TokenTypes.QUOTEDSTRING, verticleLineNumber, charPosition, stringBuilder.toString());
        }
        while (!textManager.isAtEnd() && currentCharacter != '"'){
            stringBuilder.append(currentCharacter);
        }
        return new Token(Token.TokenTypes.WORD, verticleLineNumber, charPosition, stringBuilder.toString());
    }

    private LinkedList<Token> indentTracker(int tabCounter, int spaceCounter){
        newIndentLevel = tabCounter + (spaceCounter) /4;
        var indentTokenList = new LinkedList<Token>();
        if (newIndentLevel==previousIndentLevel){
            return indentTokenList;
        }

        while(newIndentLevel!=previousIndentLevel) {
            //TODO DEBUG
            if (newIndentLevel > previousIndentLevel) {
                previousIndentLevel++;
                indentTokenList.add( new Token(Token.TokenTypes.INDENT, verticleLineNumber, charPosition));
            } else {
                if (previousIndentLevel > 0) {
                    previousIndentLevel--;
                    indentTokenList.add(new Token(Token.TokenTypes.DEDENT, verticleLineNumber, charPosition));
                }
            }
        }


        return indentTokenList;
    }



    private Token readNumber() throws SyntaxErrorException{
        boolean usedDecimal = false;
        StringBuilder stringBuilder = new StringBuilder();
        while (!textManager.isAtEnd()) { //loop continues until end of input
            char currentCharacter = textManager.peekCharacter();
            //exits if not a digit and not a decimal
            if (currentCharacter == '.' && usedDecimal == false) {
                usedDecimal = true;

            } else if(currentCharacter == '.' && usedDecimal == true){
                //TODO: thow eror
                throw new SyntaxErrorException("decimal already used in number", verticleLineNumber, charPosition);
            } else if(!Character.isDigit(textManager.peekCharacter())){

                usedDecimal = false;
                break;

            }

            stringBuilder.append(textManager.getCharacter());
            charPosition++;

        }// outside of while loop
        String numbers = stringBuilder.toString();
        return new Token(Token.TokenTypes.NUMBER, verticleLineNumber, charPosition, numbers);
    }
/// called when currentChar = letter of alphabet
    private Token readWord() {
        StringBuilder stringBuilder = new StringBuilder(); //used to append singular chars into string

        while (!textManager.isAtEnd()) { //loop continues until end of input
            char currentCharacter = textManager.peekCharacter(); //assigns char to variable for use

            if (!Character.isAlphabetic(currentCharacter) && !Character.isDigit(currentCharacter)) {
                break;
                /// ends the loop if char is not a letter, num, symbol: ie. space " "
            }
            /// adds currentChar to stringBuilder
            stringBuilder.append(textManager.getCharacter());
            charPosition++;
        }// end of while loop
        String word = stringBuilder.toString();

        /// combines all chars into single string
        if (known_KeyWords.containsKey(word)) { // checks word is a KeyWord or generic word
            return new Token(known_KeyWords.get(word), verticleLineNumber, charPosition++);
        }   //return keyword token

        return new Token(Token.TokenTypes.WORD, verticleLineNumber, charPosition++, word);
    }    //returns generic word token

            /// Method for reading symbols
    private Token readSymbol() {
        StringBuilder stringBuilder = new StringBuilder();
        char currentCharacter = textManager.peekCharacter();
        String doubleSymbolCheck = String.valueOf(currentCharacter);
        charPosition++;
        stringBuilder.append(currentCharacter); // adds current char to string builder
        if (!textManager.isAtEnd() && textManager.peekCharacter(1) == '='){
            doubleSymbolCheck += String.valueOf(textManager.peekCharacter(1));
            textManager.getCharacter();
        }

        /// checks if symbol is 2chars long

        if(known_Symbols.containsKey(doubleSymbolCheck)) {
            textManager.getCharacter(); //increments "double symbol" to avoid getting token for second char of double symbol
            return new Token(known_Symbols.get(doubleSymbolCheck), verticleLineNumber, charPosition++);
        }    //if true return token value of the key "doublSymb"
        else{ // case for if symbol is singular
            //TODO: fix return
            textManager.getCharacter();
            return new Token(known_Symbols.get(stringBuilder.toString()), verticleLineNumber, charPosition);
        }   // return hashmap token value of symbol
    }
    /// hashmap of keywords, returns token of type
    private HashMap<String, Token.TokenTypes> known_KeyWords = new HashMap<>();
    private void keyWordMap() {
        known_KeyWords.put("implements", Token.TokenTypes.IMPLEMENTS);
        known_KeyWords.put("class", Token.TokenTypes.CLASS);
        known_KeyWords.put("interface", Token.TokenTypes.INTERFACE);
        known_KeyWords.put("loop", Token.TokenTypes.LOOP);
        known_KeyWords.put("if", Token.TokenTypes.IF);
        known_KeyWords.put("else", Token.TokenTypes.ELSE);
        known_KeyWords.put("new", Token.TokenTypes.NEW);
        known_KeyWords.put("private", Token.TokenTypes.PRIVATE);
        known_KeyWords.put("shared", Token.TokenTypes.SHARED);
        known_KeyWords.put("construct", Token.TokenTypes.CONSTRUCT);
    }
    /// hashmap of symbols, returns token of type
    private HashMap<String, Token.TokenTypes> known_Symbols = new HashMap<>();
    private void symbolMap(){
        known_Symbols.put(".", Token.TokenTypes.DOT);
        known_Symbols.put("+", Token.TokenTypes.PLUS);
        known_Symbols.put("-", Token.TokenTypes.MINUS);
        known_Symbols.put("*", Token.TokenTypes.TIMES);
        known_Symbols.put("/", Token.TokenTypes.DIVIDE);
        known_Symbols.put(",", Token.TokenTypes.COMMA);
        known_Symbols.put("=", Token.TokenTypes.ASSIGN);
        known_Symbols.put("!=", Token.TokenTypes.NOTEQUAL);
        known_Symbols.put("<", Token.TokenTypes.LESSTHAN);
        known_Symbols.put("<=", Token.TokenTypes.LESSTHANEQUAL);
        known_Symbols.put(">", Token.TokenTypes.GREATERTHAN);
        known_Symbols.put(">=", Token.TokenTypes.GREATERTHANEQUAL);
        known_Symbols.put("==", Token.TokenTypes.EQUAL);
        known_Symbols.put("(", Token.TokenTypes.LPAREN);
        known_Symbols.put(")", Token.TokenTypes.RPAREN);
        known_Symbols.put(":", Token.TokenTypes.COLON);
        known_Symbols.put("!", null); // special case to assist "!=" bc of double symbol check
    }


}






