public class StringAdd {
    public static void main(String[] args) {
        String s1 = "12345";
        String s2 = "123";
        StringBuilder sb = new StringBuilder();
        int n = s1.length();
        int m = s2.length();
        char[] cs1 = s1.toCharArray();
        char[] cs2 = s2.toCharArray();

        int i = n - 1, j = m - 1;
        int carry = 0;
        while(i >= 0 || j >= 0 || carry != 0){
            int x = i >= 0 ? cs1[i] - '0' : 0;
            int y = j >= 0 ? cs2[j] - '0' : 0;
            int sum = x + y + carry;
            carry = sum / 10;
            sb.append(sum % 10);
            i--;
            j--;
        }
        sb.reverse();
        System.out.println(sb.toString());
    }
}
