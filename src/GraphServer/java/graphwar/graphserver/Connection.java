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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;

public class Connection
{

    private Channel channel;
    private io.netty.channel.nio.NioEventLoopGroup clientGroup;

    private volatile long lastReceivedTime;
    private volatile long lastSentTime;

    private java.net.Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private java.util.concurrent.BlockingQueue<String> inboundQueue;

    private static final int READ_TIMEOUT_MS = 1000;

    public Connection(Channel channel)
    {
        this.channel = channel;
        this.inboundQueue = new java.util.concurrent.LinkedBlockingQueue<>();
        this.socket = null;
        this.lastReceivedTime = System.currentTimeMillis();
        this.lastSentTime = System.currentTimeMillis();
    }

    public Connection(String host, int port) throws IOException
    {
        this.inboundQueue = new java.util.concurrent.LinkedBlockingQueue<>();
        this.lastReceivedTime = System.currentTimeMillis();
        this.lastSentTime = System.currentTimeMillis();

        if (host != null && (host.startsWith("ws://") || host.startsWith("wss://"))) {
            URI uri;
            try {
                uri = new URI(host);
            } catch (Exception e) {
                throw new IOException(e);
            }

            String scheme = uri.getScheme();
            String wsHost = uri.getHost();
            int wsPort = uri.getPort() == -1 ? ("wss".equals(scheme) ? 443 : 80) : uri.getPort();

            clientGroup = new io.netty.channel.nio.NioEventLoopGroup(1);
            Bootstrap b = new Bootstrap();
            b.group(clientGroup)
             .channel(io.netty.channel.socket.nio.NioSocketChannel.class)
             .option(ChannelOption.TCP_NODELAY, true)
             .handler(new ChannelInitializer<Channel>() {
                 @Override
                 protected void initChannel(Channel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new io.netty.handler.codec.http.HttpClientCodec());
                     p.addLast(new io.netty.handler.codec.http.HttpObjectAggregator(8192));
                     p.addLast(new io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler(
                             io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory.newHandshaker(uri,
                                     io.netty.handler.codec.http.websocketx.WebSocketVersion.V13, null, true, new io.netty.handler.codec.http.DefaultHttpHeaders())));
                     p.addLast(new SimpleChannelInboundHandler<io.netty.handler.codec.http.websocketx.TextWebSocketFrame>() {
                         @Override
                         protected void channelRead0(ChannelHandlerContext ctx, io.netty.handler.codec.http.websocketx.TextWebSocketFrame msg) throws Exception {
                             inboundQueue.offer(msg.text());
                             lastReceivedTime = System.currentTimeMillis();
                         }

                         @Override
                         public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                             cause.printStackTrace();
                             ctx.close();
                         }
                     });
                 }
             });

            ChannelFuture f = b.connect(wsHost, wsPort);
            try {
                if (!f.await(java.util.concurrent.TimeUnit.SECONDS.toMillis(10))) {
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
            return;
        }

        // Plain TCP client (existing behaviour)
        clientGroup = new io.netty.channel.nio.NioEventLoopGroup(1);
        Bootstrap b = new Bootstrap();
        b.group(clientGroup)
         .channel(io.netty.channel.socket.nio.NioSocketChannel.class)
         .option(ChannelOption.TCP_NODELAY, true)
         .handler(new ChannelInitializer<Channel>() {
             @Override
             protected void initChannel(Channel ch) {
                 ChannelPipeline p = ch.pipeline();
                 p.addLast(new io.netty.handler.codec.LineBasedFrameDecoder(8192));
                 p.addLast(new io.netty.handler.codec.string.StringDecoder(java.nio.charset.StandardCharsets.UTF_8));
                 p.addLast(new io.netty.handler.codec.string.StringEncoder(java.nio.charset.StandardCharsets.UTF_8));
                 p.addLast(new SimpleChannelInboundHandler<String>() {
                     @Override
                     protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                         inboundQueue.offer(msg);
                         lastReceivedTime = System.currentTimeMillis();
                     }

                     @Override
                     public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                         cause.printStackTrace();
                         ctx.close();
                     }
                 });
             }
         });

        ChannelFuture f = b.connect(host, port);
        try {
            if (!f.await(java.util.concurrent.TimeUnit.SECONDS.toMillis(10))) {
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

    public Connection(java.net.Socket socket) throws IOException
    {
        this.socket = socket;
        this.socket.setSoTimeout(READ_TIMEOUT_MS);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));

        this.inboundQueue = null;
        this.lastReceivedTime = System.currentTimeMillis();
        this.lastSentTime = System.currentTimeMillis();
    }

    public void close() throws IOException
    {
        if (channel != null)
        {
            channel.close();
        }

        if (clientGroup != null)
        {
            clientGroup.shutdownGracefully();
        }

        if (socket != null)
        {
            socket.close();
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

    public long getLastSentTime()
    {
        return this.lastSentTime;
    }

    public long getLastReceivedTime()
    {
        return this.lastReceivedTime;
    }

    public void sendMessage(String message)
    {
        String out = message + "\n";

        if (channel != null && channel.isActive())
        {
            if (channel.pipeline().get(io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.class) != null ||
                channel.pipeline().get(io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.class) != null) {
                channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(message));
            } else {
                channel.writeAndFlush(out);
            }
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
                e.printStackTrace();
            }
        }
    }

    public String readMessage() throws IOException, java.net.SocketTimeoutException
    {
        if (inboundQueue != null && channel != null && channel.isActive())
        {
            try
            {
                String msg = inboundQueue.poll(READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
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
