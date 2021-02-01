import java.util.*;
import java.io.*;

public class Solution {
    // constants
    static final long MOD = 1000000007L;
    static final int LIMIT = Integer.MAX_VALUE;

    static FastScanner sc;
    static PrintWriter pw;
    
    public static void solve() {
      //TODO: implement algorithm here
    }
    public static void main(String[] args) {
        sc = new FastScanner();
        pw = new PrintWriter(System.out);

        int t = sc.ni(); //number of testcases
        for(int i = 0;i<t;i++){
            solve(); 
        }
        // cleanup
        sc.close();
         pw.close();
    }

    static class FastScanner {
        BufferedReader br;
        StringTokenizer st;
      
        //! Receives input from a file
        public FastScanner(File file) {
            try {
                br = new BufferedReader(new FileReader(file));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
      
        //! Receives input from the terminal
        public FastScanner() {
            br = new BufferedReader(new InputStreamReader(System.in));
        }

        public void close() {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String next() {
            while(st == null || !st.hasMoreElements()){
                try {
                    st = new StringTokenizer(br.readLine());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return st.nextToken();
        }
        int ni(){
            return Integer.parseInt(next());
        }
        long nl(){
            return Long.parseLong(next());
        }
        double nd(){
            return Double.parseDouble(next());
        }

        /**
         * @return an array of characters from the string read using next();
         */
        char[] nc(){
            return next().toCharArray();
        }
        String nextLine(){
            String str = "";
            try{
                str = br.readLine();
            }catch(IOException e){
                e.printStackTrace();
            }
            return str;
        }
    }
}
