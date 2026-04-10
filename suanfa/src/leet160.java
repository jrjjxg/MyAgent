public class leet160 {
    public static void main(String[] args) {
        Node h1 = new Node(4);
        h1.next = new Node(1);
        h1.next.next = new Node(8);
        h1.next.next.next = new Node(4);
        h1.next.next.next.next = new Node(5);

        Node h2 = new Node(5);
        h2.next = new Node(0);
        h2.next.next = new Node(1);
        h2.next.next.next = h1.next.next;
        h2.next.next.next.next = h1.next.next.next;
        h2.next.next.next.next.next = h1.next.next.next.next;

        Node p1 = h1, p2 = h2;
        while (p1 != p2){
            p1 = p1 == null ? h2 : p1.next;
            p2 = p2 == null ? h1 : p2.next;
        }
        System.out.println(p1.val);
    }
}
