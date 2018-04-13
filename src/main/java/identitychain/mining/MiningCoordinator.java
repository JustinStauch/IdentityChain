package identitychain.mining;

import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Stores information shared among mining threads.
 */
public class MiningCoordinator {
    private int extraNonce = Integer.MIN_VALUE;
    private boolean finished = false;
    private boolean overflowed = false;
    private boolean stopped = false;

    private final Lock stopLock = new ReentrantLock();

    public synchronized int getExtraNonce() {
        if (extraNonce == Integer.MAX_VALUE) {
            overflowed = true;
            return extraNonce;
        }

        return extraNonce++;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished() {
        finished = true;
    }

    public boolean hasOverflowed() {
        return overflowed;
    }

    public boolean isStopped() {
        try {
            stopLock.lock();

            return stopped;
        } finally {
            stopLock.unlock();
        }
    }

    public void setStopped(boolean stopped) {
        try {
            stopLock.lock();
            this.stopped = stopped;
        } finally {
            stopLock.unlock();
        }
    }

    public void reset() {
        extraNonce = Integer.MIN_VALUE;
        finished = false;
        overflowed = false;
    }
}
