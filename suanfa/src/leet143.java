public class leet143 {
    static Node midnode(Node head){
        Node slow = head;
        Node fast = head;
        while (fast != null && fast.next != null){
            slow = slow.next;
            fast = fast.next.next;
        }
        return slow;
    }

    static Node rev(Node head){
        Node pre = null;
        Node cur = head;
        while (cur != null){
            Node nxt = cur.next;
            cur.next = pre;
            pre = cur;
            cur = nxt;
        }
        return pre;
    }

    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(2);
        head.next.next = new Node(3);
        head.next.next.next = new Node(4);

        Node lasthead = head;

        Node mid = midnode(head);
        Node head2 = rev(mid);

        while (head2.next != null){
            Node nxt1 = head.next;
            Node nxt2 = head2.next;
            head.next = head2;
            head2.next = nxt1;
            head = nxt1;
            head2 = nxt2;
        }

        while (lasthead != null){
            System.out.println(lasthead.val);
            lasthead = lasthead.next;
        }
    }
}
