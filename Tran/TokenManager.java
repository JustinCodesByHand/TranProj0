package Tran;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class TokenManager {
    private int currentIndex = 0;
    private List<Token> tokenList;

    public TokenManager(List<Token> tokens) {
        this.tokenList= tokens;
    }

    public boolean done() {
        return currentIndex >= tokenList.size();
    }

    public Optional<Token> matchAndRemove(Token.TokenTypes t) {
	    // is T = to type of token at top of list
        // retuen optional of token  at the top
        // if empty retuen optional empty
        if(!done() && tokenList.get(currentIndex).getType() == t){
            return Optional.of(tokenList.get(currentIndex++));
        }
        return Optional.empty();
    }

    public Optional<Token> peek(int i) {
        int position = currentIndex + i;
        if(position < tokenList.size()){
            return Optional.of(tokenList.get(position));
        }
        return Optional.empty();
    }

    public boolean nextTwoTokensMatch(Token.TokenTypes first, Token.TokenTypes second) {
	    // first and second are types of TOKENS
        // see if they are equal
        if(currentIndex + 1 < tokenList.size()){
            Token firstToken = tokenList.get(currentIndex);
            Token secondToken = tokenList.get(currentIndex + 1);
            return firstToken.getType() == first && secondToken.getType() == second;
        }
        return false;
    }

    public boolean nextIsEither(Token.TokenTypes first, Token.TokenTypes second) {
        if(!done()){
            Token current = tokenList.get(currentIndex);
            return current.getType() == first || current.getType() == second;
        }
        return false;
    }

    public int getCurrentLine() {
        if (!done()){
            return tokenList.get(currentIndex).getLineNumber();
        }
            return -1;
    }

    public int getCurrentColumnNumber() {
        if (!done()){
            return tokenList.get(currentIndex).getColumnNumber();
        }
            return -1;
    }
}
