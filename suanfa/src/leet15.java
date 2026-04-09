import java.util.*;

public class leet15 {
    public static void main(String[] args) {
        int[] arr = {-1,0,1,2,-1,-4};
        List<List<Integer>> res = new ArrayList<>();
        Arrays.sort(arr);
        int n = arr.length;
        for(int i = 0;i < n; i++){
            if(i > 0 && arr[i] == arr[i - 1]) continue;
            int l = i + 1, r = n - 1;
            while (l < r){
                int x = arr[i] + arr[l] + arr[r];
                if(x == 0){
                    res.add(Arrays.asList(arr[i],arr[l],arr[r]));
                    l++;
                    r--;
                    while (l < r && arr[l] == arr[l - 1]) l++;
                    while (l < r && arr[r] == arr[r + 1]) r--;
                }else if (x < 0){
                    l++;
                }else{
                    r--;
                }
            }
        }
        for (List<Integer> list : res) {
            System.out.println(list);
        }
    }
}
