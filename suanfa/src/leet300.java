public class leet300 {
    public static void main(String[] args) {
        int[] arr = {0,1,0,3,2,3};
        int res = 1;
        int n = arr.length;
        int[] dp = new int[n];

        for(int i = 0;i < n; i++){
            dp[i] = 1;
            for (int j = 0;j <= i; j++){
                if (arr[i] > arr[j]){
                    dp[i] = Math.max(dp[j] + 1,dp[i]);
                }
            }
            res = Math.max(res,dp[i]);
        }
        System.out.println(res);
    }
}
