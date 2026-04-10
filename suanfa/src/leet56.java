import java.util.*;

public class leet56 {
    public static void main(String[] args) {
        int[][] intervals = new int[][]{{1,3},{2,6},{8,10},{15,18}};
        Arrays.sort(intervals,(a,b) -> (a[0] - b[0]));
        int n = intervals.length;
        List<int[]> res = new ArrayList<>();
        res.add(intervals[0]);
        int end = res.get(res.size() - 1)[1];

        for(int i = 1;i < n; i++){
            if (intervals[i][0] <= end){
                end = Math.max(end,intervals[i][1]);
                res.get(res.size() - 1)[1] = end;
            }else{
                end = intervals[i][1];
                res.add(intervals[i]);
            }
        }
        for (int[] tmp : res){
            for (int x : tmp){
                System.out.print(x + " ");
            }
            System.out.println();
        }
    }
}
