class Node{
    int val;
    Node next;
    Node(){}
    Node(int val){this.val = val;}
    Node(int val,Node next){
        this.val = val;
        this.next = next;
    }
}

public class leet25 {
    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(2);
        head.next.next = new Node(3);
        head.next.next.next = new Node(4);
        head.next.next.next.next = new Node(5);
        int k = 2;

        Node tmp = head;
        int n = 0;
        while (tmp != null){
            n++;
            tmp = tmp.next;
        }

        Node dummy = new Node(0,head);
        Node p0 = dummy;
        Node pre = null;
        Node cur = head;

        for(int i = n;i >= k;i -= k){
            for(int j = 0;j < k; j++){
                Node nxt = cur.next;
                cur.next = pre;
                pre = cur;
                cur = nxt;
            }

            Node nxt = p0.next;
            p0.next.next = cur;
            p0.next = pre;
            p0 = nxt;
        }
        Node node = dummy.next;
        while (node != null){
            System.out.println(node.val);
            node = node.next;
        }
    }
}
