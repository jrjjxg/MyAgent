public class leet5 {
    static int palilen(char[] cs,int i,int j){
        while (i >= 0 && j < cs.length && cs[i] == cs[j]) {
            i--;
            j++;
        }
        return j - i - 1;
    }

    public static void main(String[] args) {
        String s = "cbbd";
        int n = s.length();
        char[] cs = s.toCharArray();
        int len = 0;
        int start = 0;
        for(int i = 0;i < n; i++){
            int x = palilen(cs,i,i);
            int y = palilen(cs,i,i+1);
            int t = Math.max(x,y);
            if(t > len){
                start = i;
                len = t;
            }
        }
        System.out.println(s.substring(start,start+len));
    }
}
