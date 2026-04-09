//import java.util.HashMap;
//
//class Node{
//    int key;
//    int value;
//    Node next;
//    Node prev;
//    public Node(){}
//    public Node(int key,int value){
//        this.key = key;
//        this.value = value;
//    }
//}
//
//class LruCache {
//    int cap;
//    public Node head, tail;
//    HashMap<Integer,Node> cache;
//
//    public LruCache(int cap){
//        this.cap = cap;
//        head = new Node();
//        tail = new Node();
//        head.next = tail;
//        tail.prev = head;
//        cache = new HashMap<>();
//    }
//
//    public int get(int key){
//        Node node = cache.get(key);
//        if(node == null) return -1;
//        movetohead(node);
//        return node.value;
//    }
//
//    public void put(int key,int value){
//        Node node = cache.get(key);
//        if(node != null){
//            node.value = value;
//            movetohead(node);
//        }else{
//            Node newnode = new Node(key,value);
//            cache.put(key,newnode);
//            if(cache.size() > cap){
//                Node tmp = removetail();
//                cache.remove(tmp.key);
//            }
//            addtohead(newnode);
//        }
//    }
//
//    private void removenode(Node node){
//        node.next.prev = node.prev;
//        node.prev.next = node.next;
//    }
//
//    private void addtohead(Node node){
//        node.next = head.next;
//        node.prev = head;
//        head.next.prev = node;
//        head.next = node;
//    }
//
//    private void movetohead(Node node){
//        removenode(node);
//        addtohead(node);
//    }
//
//    private Node removetail(){
//        Node realtail = tail.prev;
//        removenode(realtail);
//        return realtail;
//    }
//}
//
//
//public class lru {
//    public static void main(String[] args) {
//        LruCache lruCache = new LruCache(2);
//        lruCache.put(1,1);
//        lruCache.put(2,2);
//        lruCache.get(1);
//        lruCache.put(3,3);
//        lruCache.get(2);
//        lruCache.put(4,4);
//        lruCache.get(1);
//        lruCache.get(3);
//        lruCache.get(4);
//
//        Node cur = lruCache.head.next;
//        while (cur != lruCache.tail){
//            System.out.println(cur.value);
//            cur = cur.next;
//        }
//    }
//}
