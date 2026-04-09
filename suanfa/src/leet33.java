public class leet33 {

    public static void main(String[] args) {
        int[] arr = {4,5,6,7,0,1,2};
        int n = arr.length;
        int target = 8;
        int l = -1, r = n;
        int last = arr[n - 1];
        while (l + 1 < r){
            int mid = (l + r) >> 1;
            int x = arr[mid];
            if(x <= last && target > last){
                r = mid;
            }else if(x > last && target <= last){
                l = mid;
            }else if(x >= target){
                r = mid;
            }else{
                l = mid;
            }
        }
        if(arr[r] == target) System.out.println(r);
        else System.out.println(-1);
    }
}
