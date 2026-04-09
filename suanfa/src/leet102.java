import java.util.*;

class Treenode{
    int val;
    Treenode left;
    Treenode right;
    Treenode(int val){
        this.val = val;
    }
    Treenode(int val,Treenode left,Treenode right){
        this.val = val;
        this.left = left;
        this.right = right;
    }
}

public class leet102 {
    static Treenode buildtree(Integer[] tree){
        int n = tree.length;
        if(n == 0) return null;
        Treenode root = new Treenode(tree[0]);
        Queue<Treenode> q = new ArrayDeque<>();
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

    public static void main(String[] args) {
        Integer[] tree = {3,9,20,null,null,15,7};
        Treenode root = buildtree(tree);

        List<List<Integer>> res = new ArrayList<>();
        Queue<Treenode> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()){
            int n = q.size();
            List<Integer> tmp = new ArrayList<>();
            for(int i = 0;i < n; i++){
                Treenode cur = q.poll();
                if(cur.left != null) q.add(cur.left);
                if(cur.right != null) q.add(cur.right);
                tmp.add(cur.val);
            }
            res.add(tmp);
        }
        System.out.println(res);
    }
}
