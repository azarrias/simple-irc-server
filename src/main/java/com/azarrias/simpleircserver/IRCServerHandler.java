package com.azarrias.simpleircserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Handles a server-side channel.
 */
public class IRCServerHandler extends ChannelInboundHandlerAdapter { // (1)

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
	    ByteBuf in = (ByteBuf) msg;
	    try {
	    	System.out.print(in.toString(io.netty.util.CharsetUtil.UTF_8));
	    } finally {
	        in.release();
	    }
	}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // (4)
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }
}