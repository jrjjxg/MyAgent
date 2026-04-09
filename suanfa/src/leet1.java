import java.util.*;

public class leet1 {
    public static void main(String[] args) {
        int[] arr = {3,2,4};
        int target = 6;
        int n = arr.length;
        Map<Integer,Integer> map = new HashMap<>();
        for (int i = 0;i < n; i++){
            int rest = target - arr[i];
            if (map.get(rest) != null){
                System.out.println(map.get(rest));
                System.out.println(i);
            }
            map.put(arr[i],i);
        }
    }
}
