//import java.util.*;
//
//class TreeNode{
//    int value;
//    TreeNode left;
//    TreeNode right;
//    TreeNode(int value){
//        this.value = value;
//    }
//}
//
//
//public class leet236 {
//    public static TreeNode buildtree(Integer[] tree){
//        if(tree.length == 0) return null;
//        Queue<TreeNode> q = new LinkedList<>();
//        TreeNode root = new TreeNode(tree[0]);
//        q.offer(root);
//        int i = 1;
//        int n = tree.length;
//
//        while (!q.isEmpty() && i < n){
//            TreeNode tmp = q.poll();
//            if(i < n && tree[i] != null){
//                tmp.left = new TreeNode(tree[i]);
//                q.add(tmp.left);
//            }
//            i++;
//
//            if(i < n && tree[i] != null){
//                tmp.right = new TreeNode(tree[i]);
//                q.offer(tmp.right);
//            }
//            i++;
//        }
//        return root;
//    }
//
//    public static TreeNode findnode(TreeNode root,int val){
//        if(root == null) return null;
//        if(root.value == val) return root;
//        TreeNode left = findnode(root.left,val);
//        TreeNode right = findnode(root.right,val);
//        return left != null ? left : right;
//    }
//
//    // 236. 二叉树的最近公共祖先（递归解法）
//    public static TreeNode lca(TreeNode root, TreeNode p, TreeNode q) {
//        if (root == null || root == p || root == q) return root;
//        TreeNode left = lca(root.left, p, q);
//        TreeNode right = lca(root.right, p, q);
//        if (left != null && right != null) return root;
//        return left != null ? left : right;
//    }
//
//    public static void main(String[] args) {
//        Integer[] tree = {3,5,1,6,2,0,8,null,null,7,4};
//        TreeNode root = buildtree(tree);
//        int pval = 5, qval = 1;
//        TreeNode p = findnode(root,5);
//        TreeNode q = findnode(root,1);
//        TreeNode fa = lca(root,p,q);
//        System.out.println(fa.value);
//    }
//}
