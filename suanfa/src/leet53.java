public class leet53 {
    public static void main(String[] args) {
        int[] arr = {-2,1,-3,4,-1,2,1,-5,4};
        int n = arr.length;
        int[] dp = new int[n+1];
        dp[0] = 0;
        int res = Integer.MIN_VALUE;
        for(int i = 1;i < n; i++){
            dp[i] = Math.max(dp[i-1] + arr[i - 1],arr[i - 1]);
            res = Math.max(res,dp[i]);
        }
        System.out.println(res);
    }
}
