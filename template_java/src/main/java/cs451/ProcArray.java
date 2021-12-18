package cs451;

import java.util.Arrays;
import java.util.Iterator;

/**
 * `ProcArray<E>` maps a process with ID `i` to a corresponding `E`
 * Invariant: `1 <= i <= nProcs`
 */
public class ProcArray<E> implements Iterable<E> {
    private final Object[] arr;

    public ProcArray(int nProcs) {
        arr = new Object[nProcs];
    }

    /**
     * careful: can return null if the value isn't set
     */
    public E getById(int id) {
        return (E) arr[id - 1];
    }

    public void setById(int id, E v) {
        arr[id - 1] = v;
    }

    @Override
    public Iterator<E> iterator() {
        return (Iterator<E>) Arrays.stream(arr).iterator();
    }
}
