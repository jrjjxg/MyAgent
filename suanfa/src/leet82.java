public class leet82 {
    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(2);
        head.next.next = new Node(3);
        head.next.next.next = new Node(3);
        head.next.next.next.next = new Node(4);
        head.next.next.next.next.next = new Node(4);
        head.next.next.next.next.next.next = new Node(5);

        Node dummy = new Node(0,head);
        Node cur = dummy;
        while (cur.next != null){
            int val = cur.next.val;
            if (cur.next.next != null && cur.next.next.val == val){
                while (cur.next != null && cur.next.val == val){
                    cur.next = cur.next.next;
                }
            }else{
                cur = cur.next;
            }
        }

        Node node = dummy.next;
        while (node != null){
            System.out.println(node.val);
            node = node.next;
        }
    }
}
