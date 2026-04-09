//class Node{
//    int val;
//    Node next;
//    Node(){}
//    Node(int val){this.val = val;}
//    Node(int val,Node next){
//        this.val = val;
//        this.next = next;
//    }
//}
//
//public class leet206 {
//    public static void main(String[] args) {
//        Node head = new Node(1);
//        head.next = new Node(2);
//        head.next.next = new Node(3);
//        head.next.next.next = new Node(4);
//        head.next.next.next.next = new Node(5);
//
//        Node cur = head;
//        Node pre = null;
//        while (cur != null){
//            Node nxt = cur.next;
//            cur.next = pre;
//            pre = cur;
//            cur = nxt;
//        }
//
//        while (pre != null){
//            System.out.println(pre.val);
//            pre = pre.next;
//        }
//    }
//}
