public class leet165 {
    public static void main(String[] args) {
        String v1 = "1.0.1";
        String v2 = "1";
        String[] s1 = v1.split("\\.");
        String[] s2 = v2.split("\\.");

        int n = s1.length, m = s2.length;
        int i = 0, j = 0;
        while (i < n || j < m){
            int x = i < n ? Integer.parseInt(s1[i]) : 0;
            int y = j < m ? Integer.parseInt(s2[j]) : 0;
            if (x < y){
                System.out.println(-1);
                return;
            }else if (x > y){
                System.out.println(1);
                return;
            }
            i++;
            j++;
        }
        System.out.println(0);
    }
}
