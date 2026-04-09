public class leet88 {
    public static void main(String[] args) {
        int[] nums1 = {1,2,3,0,0,0};
        int[] nums2 = {2,5,6};
        int m = 2;
        int n = 2;
        int size = nums1.length - 1;
        while (m >= 0 || n >= 0){
            if (m == -1){
                nums1[size--] = nums2[n--];
            }else if (n == -1){
                nums1[size--] = nums1[m--];
            }else if(nums1[m] >= nums2[n]){
                nums1[size--] = nums1[m--];
            }else{
                nums1[size--] = nums2[n--];
            }
        }
        for (int x : nums1){
            System.out.println(x);
        }
    }
}
