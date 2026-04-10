public class leet19 {
    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(2);
        head.next.next = new Node(3);
        head.next.next.next = new Node(4);
        head.next.next.next.next = new Node(5);

        Node dummy = new Node(0,head);
        int n = 2;
        Node left = dummy;
        Node right = dummy;
        for (int i = 0;i < n; i++){
            right = right.next;
        }
        while (right.next != null){
            right = right.next;
            left = left.next;
        }
        left.next = left.next.next;

        Node node = dummy.next;
        while (node != null){
            System.out.println(node.val);
            node = node.next;
        }
    }
}
