public class leet1143 {
    public static void main(String[] args) {
        String s1 = "abcde";
        String s2 = "ace";
        char[] cs1 = s1.toCharArray();
        char[] cs2 = s2.toCharArray();
        int n = s1.length();
        int m = s2.length();

        int[][] dp = new int[n + 1][m + 1];
        for(int i = 1;i <= n; i++){
            for (int j = 1;j <= m; j++){
                if(cs1[i - 1] == cs2[j - 1]){
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                }else{
                    dp[i][j] = Math.max(dp[i - 1][j],dp[i][j - 1]);
                }
            }
        }
        System.out.println(dp[n][m]);
    }
}
