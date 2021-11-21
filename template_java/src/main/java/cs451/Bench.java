package cs451;

public class Bench {
    private final long startTime;

    public Bench() {
        startTime = System.nanoTime();
    }

    public long timeElapsedMS() {
        return (System.nanoTime() - startTime) / 1000000;
    }
}
