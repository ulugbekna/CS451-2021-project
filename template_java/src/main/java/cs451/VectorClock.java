package cs451;

/*
* We don't know how much locking is needed
* */
public class VectorClock extends ProcIntArray {
    public VectorClock(int nProcs) {
        super(nProcs);
    }

    private void incrementBy(int procId, int by) {
        setById(procId, getById(procId) + by);
    }

    public void increment(int procId) {
        incrementBy(procId, 1);
    }
}
