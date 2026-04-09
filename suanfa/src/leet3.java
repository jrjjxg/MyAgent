public class leet3 {
    public static void main(String[] args) {
        String s = "pwwkew";
        char[] cs = s.toCharArray();
        int n = s.length();
        int res = 0;
        int l = 0, r = 0;
        int[] cnt = new int[128];
        while(r < n){
            cnt[cs[r]]++;
            while(cnt[cs[r]] > 1){
                cnt[cs[l]]--;
                l++;
            }
            res = Math.max(res,r - l + 1);
            r++;
        }
        System.out.println(res);
    }

}
