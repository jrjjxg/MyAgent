public class leet415 {
    public static void main(String[] args) {
        String s1 = "11";
        String s2 = "123";
        char[] cs1 = s1.toCharArray();
        char[] cs2 = s2.toCharArray();

        int n = s1.length();
        int m = s2.length();
        int i = n - 1;
        int j = m - 1;
        int carry = 0;
        StringBuilder sb = new StringBuilder();
        while (i >= 0 || j >= 0 || carry != 0){
            int x = i >= 0 ? cs1[i] - '0' : 0;
            int y = j >= 0 ? cs2[j] - '0' : 0;
            int sum = x + y + carry;
            sb.append(sum % 10);
            carry = sum / 10;
            i--;
            j--;
        }
        sb.reverse();
        System.out.println(sb.toString());
    }
}
