package org.example.server;

import lombok.Getter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.BitSet;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

public class LoggingThread implements Runnable {

    private static final int WAIT = 10 * 1000;
    private static final int MAX_NUMBER = 999999999;
    private static final String FILE_NAME = "/tmp/numbers.log";
    static Logger log = Logger.getLogger(LoggingThread.class.getName());
    private final Object lock;
    private BitSet bitSet;
    private BlockingQueue<Integer> blockingQueue;
    @Getter
    private Statistic statistic;
    private Timer timer;
    private FileWriter file;
    private BufferedWriter out;

    //  currentThread will be initialized in the run() function
    private Thread currentThread;
    @Getter
    private boolean isShutDown;

    /**
     * Constructor
     * - Create a timer for printing the statistic
     * - Create a bitset in order to check if a number is duplicated
     * - Open the file
     */
    public LoggingThread(BlockingQueue<Integer> blockingQueue) {
        isShutDown = false;
        lock = new Object();
        bitSet = new BitSet(MAX_NUMBER + 1);
        this.blockingQueue = blockingQueue;
        statistic = new Statistic();

        timer = new Timer();
        timer.scheduleAtFixedRate(new Summary(), new Date(), WAIT);

        try {
            file = new FileWriter(FILE_NAME);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        out = new BufferedWriter(file);
    }

    /**
     * Thread run:
     * - Get a number from the blocking thread
     * - In a critical section:
     * - Check if the number is duplicated
     * - Write the number in the file.
     */
    @Override
    public void run() {
        currentThread = Thread.currentThread();
        Integer number;
        while (!currentThread.isInterrupted()) {
            try {
                number = blockingQueue.take();
            } catch (InterruptedException e) {
                // It is expected with the thread is interrupted in the function shutdown()
                log.info("The thread has been interrupted");
                // Restore interrupted state...
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (lock) {
                if (bitSet.get(number)) {
                    statistic.incrementDuplicate();
                } else {
                    try {
                        out.write(number.toString());
                        out.newLine();
                        out.flush();
                    } catch (IOException e) {
                        log.severe("Could not write '" + number + "' into the file '" + FILE_NAME + "'. Exception: " + e);
                    }

                    bitSet.set(number);
                    statistic.incrementUnique();
                }
            }
        }
    }

    public void shutdown() {
        timer.cancel();

        if (currentThread != null) {
            currentThread.interrupt();
        }

        try {
            out.close();
        } catch (IOException e) {
            log.severe("Could not close writer buffer. Exception: " + e);
        }
        try {
            file.close();
        } catch (IOException e) {
            log.severe("Could not close file '" + FILE_NAME + "'. Exception: " + e);
        }
        isShutDown = true;
    }

    final class Summary extends TimerTask {

        @Override
        public void run() {
            synchronized (lock) {
                System.out.printf(statistic.toString());
                statistic.resetUniqueCount();
                statistic.resetDuplicateCount();
            }
        }
    }
}
