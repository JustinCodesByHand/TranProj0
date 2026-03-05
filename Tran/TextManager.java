package Tran;
public class TextManager {

    private int position; // holds the position of the pointer in the string
    private String enteredText;  // string that holds the input from the file
    private char previousCharacter;

    public TextManager(String input) {
        this.enteredText = input; // instan the file variable
        this.position = 0; //instan the position var and sent to 0
    }

    public boolean isAtEnd() {
	   if (position > enteredText.length() -1) {
           return true; // if position is > string length
       }
       else{
           return false; // if position is < string length
       }
    }

    public char peekCharacter() {
            return enteredText.charAt(position);
            //returns the char at position, useful for comparisons
    }

    public char peekCharacter(int dist) {
        return enteredText.charAt(position+dist);
        // returns char at index position + dist
    }


    public char getCharacter() {
        // stores previous char before returning the next
            return enteredText.charAt(position++);
            //returns char at next position
    }

    public char getPreviousCharacter(){
        return previousCharacter;
    }


}
