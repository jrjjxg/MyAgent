class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

public class leet92 {
    public static ListNode reverseBetween(ListNode head, int left, int right) {
        ListNode dummy = new ListNode(0,head);
        ListNode p0 = dummy;
        for(int i = 0;i < left - 1; i++) p0 = p0.next;

        ListNode pre = null;
        ListNode cur = p0.next;
        for(int i = 0;i < right - left + 1; i++){
            ListNode next = cur.next;
            cur.next = pre;
            pre = cur;
            cur = next;
        }

        p0.next.next = cur;
        p0.next = pre;
        return dummy.next;
    }

    public static ListNode buildList(int[] arr) {
        ListNode dummy = new ListNode(0);
        ListNode cur = dummy;
        for (int val : arr) {
            cur.next = new ListNode(val);
            cur = cur.next;
        }
        return dummy.next;
    }

    // 辅助方法：打印链表
    public static void printList(ListNode head) {
        ListNode cur = head;
        while (cur != null) {
            System.out.print(cur.val);
            if (cur.next != null) System.out.print(" -> ");
            cur = cur.next;
        }
        System.out.println();
    }

    // 测试代码
    public static void main(String[] args) {
        // 示例 1
        int[] arr1 = {1,2,3,4,5};
        ListNode head1 = buildList(arr1);
        System.out.print("原始链表: ");
        printList(head1);
        ListNode newHead1 = reverseBetween(head1, 2, 4);
        System.out.print("反转后 (left=2, right=4): ");
        printList(newHead1);

        // 示例 2
        int[] arr2 = {5};
        ListNode head2 = buildList(arr2);
        System.out.print("\n原始链表: ");
        printList(head2);
        ListNode newHead2 = reverseBetween(head2, 1, 1);
        System.out.print("反转后 (left=1, right=1): ");
        printList(newHead2);
    }
}