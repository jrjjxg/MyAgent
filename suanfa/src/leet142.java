public class leet142 {
    public static void main(String[] args) {
        Node head = new Node(3);
        head.next = new Node(2);
        head.next.next = new Node(0);
        head.next.next.next = new Node(-4);
        head.next.next.next.next = head.next;

        //slow: b
        //fast: 2b
        //环大小: c
        //直路: a
        // 2b - b = kc, b = kc
        // b - a = kc - a
        Node slow = head;
        Node fast = head;
        while (fast != null && fast.next != null){
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast){
                while (slow != head){
                    slow = slow.next;
                    head = head.next;
                }
                break;
            }
        }
        System.out.println(slow.val);
    }
}
