package cpw.mods.forge.serverpacklocator.server;

import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

class RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOGGER = LogManager.getLogger();

    private final ServerFileManager serverFileManager;

    RequestHandler(final ServerFileManager serverFileManager) {
        this.serverFileManager = serverFileManager;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        if (Objects.equals(HttpMethod.GET, msg.method())) {
            handleGet(ctx, msg);
        } else {
            buildReply(ctx, msg, HttpResponseStatus.BAD_REQUEST, "text/plain", "Bad request");
        }
    }

    private void handleGet(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        LOGGER.info(msg.headers());

        if (Objects.equals("/servermanifest.json", msg.uri())) {
            LOGGER.info("Manifest request for client {}", determineClientIp(ctx, msg));
            final String manifest = serverFileManager.getManifestJson();
            buildReply(ctx, msg, HttpResponseStatus.OK, "application/json", manifest);
        } else if (msg.uri().startsWith("/files/")) {
            String fileName = LamdbaExceptionUtils.uncheck(() -> URLDecoder.decode(msg.uri().substring(7), StandardCharsets.UTF_8));
            byte[] file = serverFileManager.findFile(fileName);
            if (file == null) {
                LOGGER.debug("Requested file {} not found", fileName);
                build404(ctx, msg);
            } else {
                buildFileReply(ctx, msg, fileName, file);
            }
        } else {
            LOGGER.debug("Failed to understand message {}", msg);
            build404(ctx, msg);
        }
    }

    private String determineClientIp(final ChannelHandlerContext ctx, final FullHttpRequest msg)
    {
        if (msg.headers().contains("X-Forwarded-For"))
            return String.join(" via ", msg.headers().getAll("X-Forwarded-For")) + " (using Remote Address: " + ctx.channel().remoteAddress().toString() + ")";

        if (msg.headers().contains("Forwarded-For"))
            return String.join(" via ", msg.headers().getAll("Forwarded-For")) + " (using Remote Address: " + ctx.channel().remoteAddress().toString() + ")";

        return ctx.channel().remoteAddress().toString();
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (!(cause.getCause() instanceof SSLException)) {
            LOGGER.warn("Error in request handler code", cause);
        } else {
            LOGGER.trace("SSL error in handling code", cause.getCause());
        }
    }

    private void build404(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        buildReply(ctx, msg, HttpResponseStatus.NOT_FOUND, "text/plain", "Not Found");
    }

    private void buildReply(final ChannelHandlerContext ctx, final FullHttpRequest msg, final HttpResponseStatus status, final String contentType, final String message) {
        final ByteBuf content = Unpooled.copiedBuffer(message, StandardCharsets.UTF_8);
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        HttpUtil.setKeepAlive(resp, HttpUtil.isKeepAlive(msg));
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        HttpUtil.setContentLength(resp, content.writerIndex());
        ctx.writeAndFlush(resp);
    }

    private void buildFileReply(final ChannelHandlerContext ctx, final FullHttpRequest msg, final String fileName, final byte[] file) {
        final ByteBuf content = Unpooled.wrappedBuffer(file);
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
        HttpUtil.setKeepAlive(resp, HttpUtil.isKeepAlive(msg));
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        resp.headers().set("filename", fileName);
        HttpUtil.setContentLength(resp, content.writerIndex());
        ctx.writeAndFlush(resp);
    }
}
