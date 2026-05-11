package com.github.cfmsm.meteorcl.highlevel;

import java.util.concurrent.*;
import java.util.concurrent.*;

public class Sweeper {

    private final ConcurrentHashMap<AutoCloseable, Life> chm = new ConcurrentHashMap<>();
    private volatile boolean active = true;

    public Sweeper(long frequency) {
        System.out.println("Warning: High-level may be hard to debug");
        Thread.startVirtualThread(() -> {
            try {
                while (true) {
                    long start = System.currentTimeMillis();

                    if (active) {
                        long now = System.currentTimeMillis();

                        chm.forEach((key, value) -> {
                            if (now - value.lastAccess > 60_000) {
                                try {
                                    key.close();
                                    chm.remove(key);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }

                    long ms = System.currentTimeMillis() - start;
                    Thread.sleep(Math.max(frequency - ms, 1));
                }
            } catch (InterruptedException e) {
                System.err.println("Sweeper interrupted: " + e.getMessage());
            }
        });
    }

    public void touch(AutoCloseable ac) {
        Life life = chm.get(ac);
        if (life != null) {
            life.lastAccess = System.currentTimeMillis();
        }
    }

    public void add(AutoCloseable ac) {
        chm.put(ac, new Life());
    }

    public synchronized void suspend() {
        active = false;
    }

    public synchronized void activate() {
        active = true;
    }
}

class Life {
    volatile long lastAccess = System.currentTimeMillis();
}