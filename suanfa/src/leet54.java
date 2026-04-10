import java.util.ArrayList;
import java.util.List;

public class leet54 {
    public static void main(String[] args) {
        int[][] mat = {{1,2,3},{4,5,6},{7,8,9}};
        int m = mat.length;
        int n = mat[0].length;
        int high = 0, low = m - 1;
        int l = 0, r = n - 1;
        List<Integer> res = new ArrayList<>();
        while (high <= low && l <= r){
            for(int j = l;j <= r; j++){
                res.add(mat[high][j]);
            }
            high++;

            for (int i = high;i <= low; i++){
                res.add(mat[i][r]);
            }
            r--;

            if (high <= low){
                for (int j = r;j >= l; j--){
                    res.add(mat[low][j]);
                }
            }
            low--;

            if (l <= r){
                for (int i = low;i >= high; i--){
                    res.add(mat[i][l]);
                }
            }
            l++;
        }
        System.out.println(res);
    }
}
