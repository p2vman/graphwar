package graphwar.graphserver;

import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import lombok.NonNull;

import java.util.function.Function;
import java.util.function.Supplier;

public enum EventLoopGroupType {
    Nio(NioSocketChannel.class, NioServerSocketChannel.class, (cores) -> new MultiThreadIoEventLoopGroup(cores, NioIoHandler.newFactory()), () -> true),
    Epoll(EpollSocketChannel.class, EpollServerSocketChannel.class, (cores) -> new MultiThreadIoEventLoopGroup(cores, EpollIoHandler.newFactory()), io.netty.channel.epoll.Epoll::isAvailable),
    IoUring(IoUringSocketChannel.class, IoUringServerSocketChannel.class, (cores) -> new MultiThreadIoEventLoopGroup(cores, IoUringIoHandler.newFactory()), io.netty.channel.uring.IoUring::isAvailable),;

    public final Class<? extends ServerSocketChannel> serverSocketCls;
    public final Class<? extends SocketChannel> clientSocketCls;
    private final Function<Integer, MultiThreadIoEventLoopGroup> loop_factory;
    private final Supplier<Boolean> isAvailable;

    private EventLoopGroupType(@NonNull Class<? extends SocketChannel> clientSocketCls, @NonNull Class<? extends ServerSocketChannel>  loopSocketClass, @NonNull Function<Integer, @NonNull MultiThreadIoEventLoopGroup> loop_factory, @NonNull Supplier<Boolean> isAvailable) {
        this.serverSocketCls = loopSocketClass;
        this.loop_factory = loop_factory;
        this.isAvailable = isAvailable;
        this.clientSocketCls = clientSocketCls;
    }

    public MultiThreadIoEventLoopGroup newEventLoop(int cores) {
        return loop_factory.apply(cores);
    }


    public MultiThreadIoEventLoopGroup newEventLoop() {
        return loop_factory.apply(0);
    }
    public boolean isAvailable() {
        return isAvailable.get();
    }

    public static EventLoopGroupType getAvailable() {
        if (IoUring.isAvailable()) {
            return EventLoopGroupType.IoUring;
        } else if (Epoll.isAvailable()) {
            return EventLoopGroupType.Epoll;
        }
        return EventLoopGroupType.Nio;
    }

    public static EventLoopGroupType getAvailableOf(@NonNull EventLoopGroupType... types) {
        for (EventLoopGroupType type : types) {
            if (type.isAvailable()) return type;
        }
        return EventLoopGroupType.Nio;
    }
}