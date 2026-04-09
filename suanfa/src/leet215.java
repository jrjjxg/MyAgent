import java.util.*;

public class leet215 {
    private static Random rand = new Random();
    static void swap(int[] arr,int i,int j){
        int tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }

    public static int partition(int[] arr,int l,int r){
        int idx = l + rand.nextInt(r - l + 1);
        swap(arr,l,idx);
        int pivot = arr[l];
        int i = l - 1;
        int j = r + 1;
        while (true){
            do {j--;} while (arr[j] > pivot);
            do {i++;} while (arr[i] < pivot);
            if(i >= j) return j;
            swap(arr,i,j);
        }
    }

    static int qs(int[] arr,int l,int r,int k){
        if(l == r) return arr[l];
        int idx = partition(arr,l,r);
        //左半部分 nums[l...j] 里的元素都 <= pivot。
        //右半部分 nums[j+1...r] 里的元素都 >= pivot。
        if(idx >= k) return qs(arr,l,idx,k);
        return qs(arr,idx+1,r,k);
    }

    public static void main(String[] args) {
        int[] arr = {3,2,1,5,6,4};
        int k = 2;
        int n = arr.length;
        System.out.println(qs(arr,0,n - 1,n - k));
    }
}
