import java.util.*;
public class leet200 {
    static int[] dx = {0,0,-1,1};
    static int[] dy = {1,-1,0,0};

    static char[][] grid = {
            {'1', '1', '0', '0', '0'},
            {'1', '1', '0', '0', '0'},
            {'0', '0', '1', '0', '0'},
            {'0', '0', '0', '1', '1'}
    };

    static int n = grid.length;
    static int m = grid[0].length;

    public static void main(String[] args) {
        int res = 0;
        for(int i = 0;i< n; i++){
            for(int j = 0;j < m; j++){
                if(grid[i][j] == '0') continue;
                dfs(i,j);
                res++;
            }
        }
        System.out.println(res);
    }

    static void dfs(int x,int y){
        grid[x][y] = '0';
        for(int i = 0;i < 4; i++){
            int nx = x + dx[i];
            int ny = y + dy[i];
            if(nx < 0 || nx == n || ny < 0 || ny == m || grid[nx][ny] == '0') continue;
            dfs(nx,ny);
        }
    }
}
