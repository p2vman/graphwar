package graphwar.graphserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyServerHandler extends ChannelInboundHandlerAdapter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyServerHandler.class);
    private final GraphServer server;
    private ClientConnection clientConnection;

    public NettyServerHandler(GraphServer server)
    {
        this.server = server;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        Channel ch = ctx.channel();
        Connection conn = new Connection(ch);
        clientConnection = new ClientConnection(server, conn);
        server.addClient(clientConnection);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        String message = (String) msg;

        if (clientConnection != null)
        {
            clientConnection.getConnection().touchReceived();

            if (message == null)
            {
                server.removeClient(clientConnection);
                clientConnection.disconnect();
            }
            else
            {
                server.handleMessage(message, clientConnection);

                if (clientConnection.checkStayAliveTime())
                {
                    clientConnection.sendKeepAlive();
                }
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        if (evt instanceof IdleStateEvent)
        {
            IdleStateEvent e =
                    (IdleStateEvent) evt;

            if (e.state() == IdleState.READER_IDLE)
            {
                if (clientConnection != null && clientConnection.checkTimeout())
                {
                    server.removeClient(clientConnection);
                    clientConnection.disconnect();
                }
                else if (clientConnection != null)
                {
                    clientConnection.sendKeepAlive();
                }
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        if (clientConnection != null)
        {
            server.removeClient(clientConnection);
            clientConnection.disconnect();
            clientConnection = null;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        LOGGER.error("Throw: ", cause);
        if (clientConnection != null)
        {
            server.removeClient(clientConnection);
            clientConnection.disconnect();
            clientConnection = null;
        }
        ctx.close();
    }
}