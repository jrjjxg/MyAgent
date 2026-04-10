public class leet83 {
    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(1);
        head.next.next = new Node(2);
        head.next.next.next = new Node(3);
        head.next.next.next.next = new Node(3);

        Node cur = head;
        while (cur != null){
            while (cur.next != null && cur.next.val == cur.val){
                cur.next = cur.next.next;
            }
            cur = cur.next;
        }
        while (head != null){
            System.out.println(head.val);
            head = head.next;
        }
    }
}
