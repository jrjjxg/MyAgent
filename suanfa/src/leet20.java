import java.util.*;

public class leet20 {
    public static void main(String[] args) {
        String s = "()[]{]";
        char[] cs = s.toCharArray();
        int n = s.length();
        Deque<Character> st = new ArrayDeque<>();
        for (char c : cs){
            if(c == '(' || c == '[' || c == '{'){
                st.push(c);
            }else{
                char tmp = st.pop();
                if ((tmp == '(' && c != ')') || (tmp == '[' && c != ']') || (tmp == '{' && c != '}')){
                    System.out.println("false");
                    return;
                }
            }
        }
        System.out.println("true");
    }
}
