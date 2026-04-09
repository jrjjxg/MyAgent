public class leet121 {
    public static void main(String[] args) {
        int[] arr = {7,6,4,3,1};
        int n = arr.length;
        int low = arr[0];
        int res = 0;
        for(int i = 1;i < n; i++){
            res = Math.max(res,arr[i] - low);
            low = Math.min(low,arr[i]);
        }
        System.out.println(res);
    }
}
