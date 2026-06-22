//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//    
//  This file is part of Graphwar.
//
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  Graphwar is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.

//  You should have received a copy of the GNU General Public License
//  along with Graphwar.  If not, see <http://www.gnu.org/licenses/>.

package graphwar.graphserver;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Connection
{

    private Channel channel;
    private EventLoopGroup clientGroup;

    private static final Logger LOGGER = LoggerFactory.getLogger(Connection.class);

    @Getter
    private volatile long lastReceivedTime;
    @Getter
    private volatile long lastSentTime;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private final BlockingQueue<String> inboundQueue;

    private static final int READ_TIMEOUT_MS = 1000;

    public Connection(Channel channel)
    {
        this.channel = channel;
        this.inboundQueue = new LinkedBlockingQueue<>();
        this.socket = null;
        this.lastReceivedTime = System.currentTimeMillis();
        this.lastSentTime = System.currentTimeMillis();
    }

    public Connection(String host, int port) throws IOException
    {
        this.inboundQueue = new LinkedBlockingQueue<>();
        this.lastReceivedTime = System.currentTimeMillis();
        this.lastSentTime = System.currentTimeMillis();


        EventLoopGroupType groupType = EventLoopGroupType.getAvailable();
        clientGroup = groupType.newEventLoop(1);
        Bootstrap b = new Bootstrap();
        b.group(clientGroup)
         .channel(groupType.clientSocketCls)
         .option(ChannelOption.TCP_NODELAY, true)
         .handler(new ChannelInitializer<Channel>() {
             @Override
             protected void initChannel(Channel ch) {
                 ChannelPipeline p = ch.pipeline();
                 p.addLast(new LineBasedFrameDecoder(8192));
                 p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                 p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                 p.addLast(new SimpleChannelInboundHandler<String>() {
                     @Override
                     protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                         inboundQueue.offer(msg);
                         lastReceivedTime = System.currentTimeMillis();
                     }

                     @Override
                     public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                         LOGGER.error("Throw: ", cause);
                         ctx.close();
                     }
                 });
             }
         });

        ChannelFuture f = b.connect(host, port);
        try {
            if (!f.await(TimeUnit.SECONDS.toMillis(10))) {
                clientGroup.shutdownGracefully();
                throw new IOException("Connection timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }

        if (!f.isSuccess()) {
            clientGroup.shutdownGracefully();
            throw new IOException(f.cause());
        }

        this.channel = f.channel();
    }

    public Connection(Socket socket) throws IOException
    {
        this.socket = socket;
        this.socket.setSoTimeout(READ_TIMEOUT_MS);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        this.inboundQueue = null;
        this.lastReceivedTime = System.currentTimeMillis();
        this.lastSentTime = System.currentTimeMillis();
    }

    public void close() throws IOException
    {
        if (channel != null)
        {
            try {
                channel.close().syncUninterruptibly();
            } catch (Exception e) {
                // ignore
            } finally {
                channel = null;
            }
        }

        if (clientGroup != null)
        {
            try {
                clientGroup.shutdownGracefully().await(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // ignore
            } finally {
                clientGroup = null;
            }
        }

        if (socket != null)
        {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            } finally {
                socket = null;
            }
        }

        if (reader != null) {
            try { reader.close(); } catch (IOException e) { }
            reader = null;
        }
        if (writer != null) {
            try { writer.close(); } catch (IOException e) { }
            writer = null;
        }
    }

    public String getIpAddress()
    {
        if (channel != null && channel.remoteAddress() instanceof java.net.InetSocketAddress)
        {
            return ((java.net.InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress();
        }
        else if (socket != null)
        {
            return socket.getInetAddress().getHostAddress();
        }
        return "";
    }

    public void sendMessage(String message)
    {
        String out = message + "\n";

        if (channel != null && channel.isActive())
        {
            channel.writeAndFlush(out);
            lastSentTime = System.currentTimeMillis();
            return;
        }

        if (writer != null)
        {
            try
            {
                writer.write(out);
                writer.flush();
                lastSentTime = System.currentTimeMillis();
            }
            catch (IOException e)
            {
                LOGGER.error("Throw: ", e);
            }
        }
    }

    public String readMessage() throws IOException
    {
        if (inboundQueue != null && channel != null && channel.isActive())
        {
            try
            {
                String msg = inboundQueue.poll(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (msg == null)
                {
                    throw new java.net.SocketTimeoutException();
                }
                lastReceivedTime = System.currentTimeMillis();
                return msg;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        if (reader != null)
        {
            try
            {
                String line = reader.readLine();
                if (line == null)
                {
                    return null;
                }
                lastReceivedTime = System.currentTimeMillis();
                return line;
            }
            catch (java.net.SocketTimeoutException e)
            {
                throw e;
            }
        }

        return null;
    }

    public void offerInbound(String msg)
    {
        if (inboundQueue != null)
        {
            inboundQueue.offer(msg);
            lastReceivedTime = System.currentTimeMillis();
        }
    }

    public void touchReceived()
    {
        lastReceivedTime = System.currentTimeMillis();
    }

    public boolean isActive()
    {
        if (channel != null)
        {
            return channel.isActive();
        }
        if (socket != null)
        {
            return !socket.isClosed();
        }
        return false;
    }
}
