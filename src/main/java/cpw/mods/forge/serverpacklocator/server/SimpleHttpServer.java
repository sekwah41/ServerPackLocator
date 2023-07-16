package cpw.mods.forge.serverpacklocator.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

/**
 * Simple Http Server for serving file and manifest requests to clients.
 */
public class SimpleHttpServer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final NioEventLoopGroup PARENT_GROUP = new NioEventLoopGroup(1, new ThreadFactoryBuilder()
            .setNameFormat("ServerPack Locator Parent - %d")
            .setDaemon(true)
            .build());
    private static final NioEventLoopGroup CHILD_GROUP = new NioEventLoopGroup(1, new ThreadFactoryBuilder()
            .setNameFormat("ServerPack Locator Child - %d")
            .setDaemon(true)
            .build());

    private static final int MAX_CONTENT_LENGTH = 2 << 19;

    private SimpleHttpServer() {
        throw new IllegalArgumentException("Can not instantiate SimpleHttpServer.");
    }

    public static void run(final ServerFileManager fileManager, final int port, @Nullable final SslContext sslContext) {
        final ServerBootstrap bootstrap = new ServerBootstrap()
                .group(PARENT_GROUP, CHILD_GROUP)
                .channel(NioServerSocketChannel.class)
                .handler(new ChannelInitializer<ServerSocketChannel>() {
                    @Override
                    protected void initChannel(final ServerSocketChannel channel) {
                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(final ChannelHandlerContext ctx) {
                                LOGGER.info("ServerPack server active on port {}", port);
                            }
                        });
                    }
                })
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel channel) {
                        if (sslContext != null) {
                            channel.pipeline().addLast("ssl", sslContext.newHandler(channel.alloc()));
                        }
                        channel.pipeline().addLast("codec", new HttpServerCodec());
                        channel.pipeline().addLast("aggregator", new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                        channel.pipeline().addLast("request", new RequestHandler(fileManager));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.bind(port).syncUninterruptibly();
    }
}
