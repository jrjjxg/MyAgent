public class leet141 {
    public static void main(String[] args) {
        Node head = new Node(1);


        Node slow = head;
        Node fast = head;
        while (fast != null && fast.next != null){
            slow = slow.next;
            fast = fast.next.next;
            if(slow == fast) {
                System.out.println("true");
                return;
            }
        }
        System.out.println("false");
        return;
    }
}
