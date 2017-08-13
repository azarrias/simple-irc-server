package com.azarrias.simpleircserver;

import java.util.Vector;
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
    private static ConcurrentMap<String, Vector<String>> lastActivityLogs = new ConcurrentHashMap<String, Vector<String>>();
    
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Channel incoming = ctx.channel();
        incoming.writeAndFlush("[ " + IRC_USER + "] - Welcome to this IRC Server\r\n" + 
        		"Command set:\r\n  /login username password\r\n  /join channel\r\n  /leave\r\n  /users\r\n\r\n");

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
        		incoming.writeAndFlush("[" + IRC_USER + "] - Invalid command.\r\n\r\n");
        	break;
        case "/join":
        	if (tokens.length == 2)
        		join(ctx, tokens[1]);
        	else 
        		incoming.writeAndFlush("[" + IRC_USER + "] - Invalid command.\r\n\r\n");
        	break;
        case "/leave":
        	if (tokens.length == 1){
        		leave(ctx);
        	}
        	else
        		incoming.writeAndFlush("[" + IRC_USER + "] - Invalid command.\r\n\r\n");
        	break;
        case "/users":
        	if (tokens.length == 1)
        		showUsers(ctx);
        	else
        		incoming.writeAndFlush("[" + IRC_USER + "] - Invalid command.\r\n\r\n");
        	break;
        default:
        	sendMessage(ctx, str);
        }
    }

	private void leave(ChannelHandlerContext ctx) {
		Channel incoming = ctx.channel();
		incoming.writeAndFlush("[" + IRC_USER + "] - Leaving... \r\n\r\n");
		
		// If the user is inside a channel, notify other users (Logged activity)
		String previousChannelName = ircChannels.get(incoming.id());
		if(previousChannelName != null){
			String msg = "[" + IRC_USER + "] - " + ircUsers.get(incoming.id()) + " has left the channel.";
			addMsgToActivityLogs(previousChannelName, msg);
			for (Channel c : channels){
				if(ircChannels.get(c.id()).equals(previousChannelName) && !c.equals(incoming)){
					c.writeAndFlush(msg + "\r\n\r\n");
				}
			}
		}
		// Close connection
		ctx.close();
	}

	private void sendMessage(ChannelHandlerContext ctx, String str) {
		Channel incoming = ctx.channel();
		String channelName = ircChannels.get(incoming.id());
		String message = "[" + ircUsers.get(incoming.id()) + "] - " + str;
		
		// Send text message to the other users in the same channel (Logged activity)
		if(channelName != null){
			addMsgToActivityLogs(channelName, message);
			for (Channel c : channels){
				if(ircChannels.get(c.id()).equals(channelName) && !c.equals(incoming)){
					c.writeAndFlush(message + "\r\n");
				}
			}
		}
		else
			incoming.writeAndFlush("[" + IRC_USER + "] - You are not in a channel.\r\n\r\n");
	}

	private synchronized void login(ChannelHandlerContext ctx, String username, String password) {
    	Channel incoming = ctx.channel();
    	if(userProfiles.containsKey(username)){
    		if(userProfiles.get(username).equals(password)){
    			incoming.writeAndFlush("[" + IRC_USER + "] - User successfully logged in.\r\n\r\n");
    		}
    		else {
    			incoming.writeAndFlush("[" + IRC_USER + "] - Wrong password.\r\n\r\n");
    			return;
    		}
    	}
    	else{
    		incoming.writeAndFlush("[" + IRC_USER + "] - User successfully registered.\r\n\r\n");
    		userProfiles.put(username, password);
    	}
    	
    	ircUsers.put(incoming.id(), username);
    	ircChannels.remove(incoming.id());
	}

    private synchronized void join(ChannelHandlerContext ctx, String channelName) {
		Channel incoming = ctx.channel();
		String msg;
		
		// Check if the user is logged in
		if(!ircUsers.containsKey(incoming.id())){
			incoming.writeAndFlush("[" + IRC_USER + "] - You are not logged in.\r\n\r\n");
			return;
		}
		
		// If the channel's active client limit is not exceeded, the user can join the channel
		int counter = 0;
		for (String v : ircChannels.values())
		{
			if (channelName.equals(v) &&
				++counter >= MAX_CLIENTS_PER_CHANNEL) {
					incoming.writeAndFlush("[" + IRC_USER + "] - Channel " + channelName + " is currently full.\r\n\r\n");
					return;
			}
		}
		
		// If the user was previously in a different channel notify leave to others (Logged activity)
		String previousChannelName = ircChannels.get(incoming.id());
		if(previousChannelName != null){
			incoming.writeAndFlush("[" + IRC_USER + "] - Left channel " + previousChannelName + ".\r\n");
			msg = "[" + IRC_USER + "] - " + ircUsers.get(incoming.id()) + " has left the channel.";
			addMsgToActivityLogs(previousChannelName, msg);
			for (Channel c : channels){
				if(ircChannels.get(c.id()).equals(previousChannelName) && !c.equals(incoming)){
					c.writeAndFlush(msg + "\r\n\r\n");
				}
			}
		}
		
		// User joins channel
		incoming.writeAndFlush("[" + IRC_USER + "] - Joined channel " + channelName + ".\r\n");
		ircChannels.put(incoming.id(), channelName);
		
		// Notification to other users (Logged activity)
		msg = "[" + IRC_USER + "] - " + ircUsers.get(incoming.id()) + " has joined the channel.";
		addMsgToActivityLogs(channelName, msg);
		for (Channel c : channels){
			if(ircChannels.get(c.id()).equals(channelName) && !c.equals(incoming)){
				c.writeAndFlush(msg + "\r\n\r\n");
			}
		}
		
		// Show this channel's last N messages of activity to the joining user
		if(lastActivityLogs.get(channelName) != null){
			for(String str : lastActivityLogs.get(channelName)){
				incoming.writeAndFlush(str + "\r\n");
			}
		}
		incoming.writeAndFlush("\r\n");
	}
    
	private synchronized void addMsgToActivityLogs(String channelName, String msg) {
		// Add activity to last channel activity logs
		Vector<String> activity = lastActivityLogs.get(channelName);
		if (activity == null) {
			activity = new Vector<String>();
			lastActivityLogs.put(channelName, activity);
		}
		activity.add(msg);
		while (activity.size() > LAST_N_MESSAGES)
			activity.remove(0);
	}

	private void showUsers(ChannelHandlerContext ctx) {
		Channel incoming = ctx.channel();
		String channelName = ircChannels.get(incoming.id());
		if(channelName != null) {
			incoming.writeAndFlush("[" + IRC_USER + "] - List of users in channel " + channelName + ":\r\n");
			for (Channel c : channels) {
				if(channelName.equals(ircChannels.get(c.id()))){
					incoming.writeAndFlush(ircUsers.get(c.id()) + "\r\n");
				}
			}
			incoming.writeAndFlush("\r\n");
		}
		else{
			incoming.writeAndFlush("[" + IRC_USER + "] - You are not in a channel.\r\n\r\n");
		}
	}
	
	@Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    	// Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }

}