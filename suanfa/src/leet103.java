import java.util.*;

class Treenode{
    int val;
    Treenode left;
    Treenode right;
    Treenode(int val){
        this.val = val;
    }
}

public class leet103 {
    static Treenode buildtree(Integer[] tree){
        int n = tree.length;
        if(n == 0) return null;
        Queue<Treenode> q = new ArrayDeque<>();
        Treenode root = new Treenode(tree[0]);
        q.add(root);
        int i = 1;

        while (!q.isEmpty() && i < n){
            Treenode cur = q.poll();
            if(i < n && tree[i] != null){
                cur.left = new Treenode(tree[i]);
                q.add(cur.left);
            }
            i++;

            if(i < n && tree[i] != null){
                cur.right = new Treenode(tree[i]);
                q.add(cur.right);
            }
            i++;
        }
        return root;
    }

    static List<List<Integer>> solve(Treenode root){
        List<List<Integer>> res = new ArrayList<>();
        Queue<Treenode> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()){
            int n = q.size();
            List<Integer> tmp = new ArrayList<>();
            for(int i = 0;i < n; i++){
                Treenode cur = q.poll();
                tmp.add(cur.val);
                if(cur.left != null) q.add(cur.left);
                if(cur.right != null) q.add(cur.right);
            }
            if(res.size() % 2 == 1){
                Collections.reverse(tmp);
            }
            res.add(tmp);
        }
        return res;
    }

    public static void main(String[] args) {
        Integer[] tree = {3,9,20,null,null,15,7};
        Treenode root = new Treenode(3);
        root.left = new Treenode(9);
        root.right = new Treenode(20);
        root.right.left = new Treenode(15);
        root.right.right = new Treenode(7);

        List<List<Integer>> res = solve(root);
        System.out.println(res);
    }
}
