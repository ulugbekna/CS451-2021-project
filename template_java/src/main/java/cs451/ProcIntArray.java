package cs451;

import java.util.Arrays;
import java.util.Iterator;

public class ProcIntArray implements Iterable<Integer> {
    private final int[] arr;

    public ProcIntArray(int nProcs) {
        arr = new int[nProcs];
    }

    /**
     * careful: can return null if the value isn't set
     */
    public int getById(int id) {
        return arr[id - 1];
    }

    public void setById(int id, int v) {
        arr[id - 1] = v;
    }

    @Override
    public Iterator<Integer> iterator() {
        return Arrays.stream(arr).iterator();
    }

    @Override
    public String toString() {
        return Arrays.toString(arr);
    }
}
