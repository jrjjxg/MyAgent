import java.util.*;

 class leet46 {
    static List<List<Integer>> res = new ArrayList<>();
    static List<Integer> tmp = new ArrayList<>();
    static int[] arr = {1,2,3};
    static int n = arr.length;
    static boolean[] used = new boolean[n];
    static void dfs(){
        if(tmp.size() == n) {
            res.add(new ArrayList<>(tmp));
            return;
        }

        for(int i = 0;i < n; i++){
            if(used[i]) continue;
            used[i] = true;
            tmp.add(arr[i]);
            dfs();
            used[i] = false;
            tmp.remove(tmp.size() - 1);
        }
    }

    public static void main(String[] args) {
        dfs();
        System.out.println(res);
    }
}
