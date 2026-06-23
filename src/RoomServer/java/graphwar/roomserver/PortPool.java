package graphwar.roomserver;

import it.unimi.dsi.fastutil.ints.IntArrayPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PortPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortPool.class);
    private final IntPriorityQueue free_ports = new IntArrayPriorityQueue();
    private final IntSet bind_ports = new IntArraySet();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    public PortPool() {

    }

    public void addPort(int port) {
        Lock lock = this.lock.writeLock();
        lock.lock();
        try {
            free_ports.enqueue(port);
        } finally {
            lock.unlock();
        }
    }

    public int bind() {
        Lock lock = this.lock.writeLock();
        lock.lock();
        try {
            if (!free_ports.isEmpty()) {
                int port = free_ports.dequeueInt();
                bind_ports.add(port);
                LOGGER.info("Bind port: {}", port);
                return port;
            }
        } finally {
            lock.unlock();
        }
        throw new RuntimeException();
    }

    public void unbind(int port) {
        Lock lock = this.lock.writeLock();
        lock.lock();
        try {
            if (bind_ports.contains(port)) {
                bind_ports.remove(port);
                free_ports.enqueue(port);
                LOGGER.info("UnBind port: {}", port);
            }
        } finally {
            lock.unlock();
        }
    }
}
