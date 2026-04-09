import java.util.Random;

public class quicksort {
    static Random rand = new Random();

    private static void swap(int[] arr,int i,int j){
        int tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }

    static int partition(int[] arr,int l,int r){
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

    static void quicksort(int[] arr,int l,int r){
        if(l == r) return;
        int idx = partition(arr,l,r);
        quicksort(arr,l,idx);
        quicksort(arr,idx+1,r);
    }

    public static void main(String[] args) {
        int[] arr = {5,1,1,2,0,0};
        quicksort(arr,0,5);
        for(int x : arr){
            System.out.println(x);
        }
    }
}
