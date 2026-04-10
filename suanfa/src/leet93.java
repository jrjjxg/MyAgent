import java.util.*;

public class leet93 {
    static boolean isvalid(String s){
        if (s.length() > 1 && s.charAt(0) == '0') return false;
        int x = Integer.parseInt(s);
        if (x < 0 || x > 255) return false;
        return true;
    }

    static List<String> tmp = new ArrayList<>();
    static List<String> res = new ArrayList<>();

    static void dfs(String s,int k){
        if (tmp.size() == 4 && k == s.length()){
            StringBuilder sb = new StringBuilder();
            for(int i = 0;i < 4; i++){
                sb.append(tmp.get(i));
                if(i != 3) sb.append('.');
            }
            res.add(sb.toString());
            return;
        }

        for (int i = k;i < s.length() && i < k + 3; i++){
            String str = s.substring(k,i + 1);
            if (!isvalid(str)) break;
            tmp.add(str);
            dfs(s,i + 1);
            tmp.remove(str);
        }
    }

    public static void main(String[] args) {
        String s = "25525511135";
        dfs(s,0);
        System.out.println(res);
    }
}
