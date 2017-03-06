package org.dice.parsing;

import org.dice.parsing.ast.Expression;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by simon.hughes on 4/13/16.
 */
public class InteractiveConsole {

    public static void main(String[] args) throws IOException {

        String get_title_prompt = "Please enter search query to parse:";
        String user_input = get_user_input(get_title_prompt);
        while (!user_input.equals("exit") && !user_input.trim().equals(""))
        {
            try{
                RecursiveDescentParser parser = new RecursiveDescentParser(new Lexer(user_input), "*:*");
                Expression ast = parser.parse();
                System.out.println("Parsed: " + ast.evaluate());
                if(parser.hasErrors()){
                    System.out.println("*** Error ***");
                }
            }
            catch (Exception ex){
                System.out.println("Parsing Error:\n" + ex.toString());
            }

            System.out.println();
            user_input = get_user_input(get_title_prompt);
        }
    }

    private static String get_user_input(String prompt) {
        System.out.println(prompt);
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            return in.readLine().trim().toLowerCase();
        } catch (Exception ex) {
            System.out.println("Error:\n" + ex.toString());
            return "";
        }
    }

}
