import java.util.*;

public class leet23 {
    public static void main(String[] args) {
        List<Node> list = new ArrayList<>();
        Node h1 = new Node(1);
        h1.next = new Node(4);
        h1.next.next = new Node(5);

        Node h2 = new Node(1);
        h2.next = new Node(3);
        h2.next.next = new Node(4);

        Node h3 = new Node(2);
        h3.next = new Node(6);

        list.add(h1);
        list.add(h2);
        list.add(h3);

        PriorityQueue<Node> pq = new PriorityQueue<>((a,b) ->(a.val - b.val));
        for (Node head : list){
            if (head != null){
                pq.add(head);
            }
        }

        Node dummy = new Node();
        Node cur = dummy;
        while (!pq.isEmpty()){
            Node node = pq.poll();
            if (node.next != null){
                pq.add(node.next);
            }
            cur.next = node;
            cur = cur.next;
        }

        Node head = dummy.next;
        while (head != null){
            System.out.println(head.val);
            head = head.next;
        }
    }
}
