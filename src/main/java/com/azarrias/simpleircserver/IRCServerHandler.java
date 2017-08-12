package com.azarrias.simpleircserver;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.netty.channel.Channel;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;

/**
 * Handles a server-side channel.
 */
public class IRCServerHandler extends SimpleChannelInboundHandler<String> {

	private static final String IRC_USER = "IRCServer";
	private static final int MAX_CLIENTS_PER_CHANNEL = 10;
	private static final int LAST_N_MESSAGES = 5;
	private static final ChannelGroup channels = new DefaultChannelGroup(new DefaultEventExecutor());
    private static ConcurrentMap<ChannelId, String> ircChannels = new ConcurrentHashMap<ChannelId, String>();
    private static ConcurrentMap<ChannelId, String> ircUsers = new ConcurrentHashMap<ChannelId, String>();
    private static ConcurrentMap<String, String> userProfiles = new ConcurrentHashMap<String, String>();
    
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Channel incoming = ctx.channel();
        incoming.writeAndFlush("[ " + IRC_USER + "] - Welcome to this IRC Server\r\n" + 
        		"Command set:\r\n  /login username password\r\n  /join channel\r\n  /leave\r\n  /users\r\n> ");

        channels.add(incoming);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel incoming = ctx.channel();
        
        channels.remove(incoming);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String str) throws Exception {
        Channel incoming = ctx.channel();
        String[] tokens = str.split("\\s+");
        
        switch(tokens[0])
        {
        case "/login":
        	if (tokens.length == 3)
        		login(ctx, tokens[1], tokens[2]);
        	else 
        		incoming.writeAndFlush("Invalid command.\r\n> ");
        	break;
        case "/join":
        	if (tokens.length == 2)
        		join(ctx, tokens[1]);
        	else 
        		incoming.writeAndFlush("Invalid command.\r\n> ");
        	break;
        default:
        	incoming.writeAndFlush("Invalid command.\r\n> ");
        }
    }

	private synchronized void login(ChannelHandlerContext ctx, String username, String password) {
    	Channel incoming = ctx.channel();
    	if(userProfiles.containsKey(username)){
    		if(userProfiles.get(username).equals(password)){
    			incoming.writeAndFlush("[" + IRC_USER + "] - User successfully logged in.\r\n> ");
    		}
    		else {
    			incoming.writeAndFlush("[" + IRC_USER + "] - Wrong password.\r\n> ");
    			return;
    		}
    	}
    	else{
    		incoming.writeAndFlush("[" + IRC_USER + "] - User successfully registered.\r\n> ");
    		userProfiles.put(username, password);
    	}
    	
    	ircUsers.put(incoming.id(), username);
	}

    private synchronized void join(ChannelHandlerContext ctx, String channelName) {
		Channel incoming = ctx.channel();
		// If the client's limit is not exceeded, join channel
		int counter = 0;
		for (String v : ircChannels.values())
		{
			if (channelName.equals(v) &&
				++counter >= MAX_CLIENTS_PER_CHANNEL) {
					incoming.writeAndFlush("[" + IRC_USER + "] - Channel " + channelName + " is currently full.\r\n> ");
					return;
			}
		}
		incoming.writeAndFlush("[" + IRC_USER + "] - Joined channel " + channelName + ".\r\n> ");	
		ircChannels.put(incoming.id(), channelName);
	}
	
	@Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    	// Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }

}