package uk.co.uwcs.choob.support;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import uk.co.uwcs.choob.Choob;

/**
 *
 * @author Candle
 */
public class ChoobMessageQueue
{

	private Handler pm;
	private Handler cm;
	private Handler out;
	private Choob bot;

	public ChoobMessageQueue(Choob bot)
	{
		if (bot == null) throw new IllegalArgumentException("The bot cannot be null.");

		this.bot = bot;
		out = new OutgoingHandler();
		pm = new PrivateHandler(out, 3, 2000);
		cm = new ChannelHandler(out);

		ThreadGroup messageQueueGroup = new ThreadGroup("Message Queue Group");
		Thread o = new Thread(messageQueueGroup, out, "Out Handler");
		Thread p = new Thread(messageQueueGroup, pm, "Private Handler");
		Thread c = new Thread(messageQueueGroup, cm, "Channel Handler");
		o.setDaemon(true);
		p.setDaemon(true);
		c.setDaemon(true);
		o.start();
		p.start();
		c.start();
	}

	private void actuallySendMessage(Message m)
	{
		if (m == null) throw new IllegalArgumentException("A null message cannot be sent");
		bot.sendMessage(m.getTarget(), m.getData());
		//logger.info("actually sending message: type: {} content: {}", m.getType(), m.getData());
	}

	public boolean hasWaitingMessages()
	{
		return !pm.isEmpty() || !cm.isEmpty() || !out.isEmpty();
	}

	public void resetQueues()
	{
		pm.clear();
		cm.clear();
		out.clear();
	}

	/**
	 * This causes the threads to end, any messages in the queues will be lost.
	 */
	public void endThreads()
	{
		pm.end();
		cm.end();
		out.end();
	}

	/**
	 * add a generic message, logic in this method decides which queue to
	 * add the message to.
	 * @param s
	 */
	public void postMessage(String target, String data)
	{
		// decide if it is a channel or a private message
		if (target.startsWith("#"))
		{
			//quick hack to say this is a channel message.
			cm.postMessage(new Message(target, data, MessageType.CHANNEL));
		} else
		{
			pm.postMessage(new Message(target, data, MessageType.PRIVATE));
		}
	}

	/**
	 * generic queue handling and blocking
	 */
	abstract static class Handler implements Runnable
	{
		BlockingQueue<Message> messages = new LinkedBlockingQueue<Message>(); // unbounded queue size.
		boolean running = true;

		abstract void handleMessage(Message m);

		@Override
		public void run()
		{
			//logger.debug("Handler of type {} starting", getClass());
			while (running)
			{
				try
				{
					Message m = messages.take();
					if (m.getType() == MessageType.END)
						running = false;
					else
						handleMessage(m);
				} catch (InterruptedException ex)
				{
					//logger.warn("Handler 'take' interrupted.");
				}
			}
			//logger.debug("Handler of type {} finishing", getClass());
		}

		public void postMessage(Message m)
		{
			messages.add(m);
		}

		private void clear()
		{
			messages.clear();
		}

		private void end()
		{
			clear();
			postMessage(new Message(null, null, MessageType.END));
		}

		private boolean isEmpty()
		{
			return messages.isEmpty();
		}
	}

	/**
	 * Any extra filtering or delaying can be done here.
	 */
	class OutgoingHandler extends Handler
	{

		@Override
		void handleMessage(Message m)
		{
			actuallySendMessage(m);
		}
	}

	/**
	 * If the PM queue has more then X elements, then this delays
	 * messages by Y miliseconds.
	 */
	static class PrivateHandler extends Handler
	{

		Handler outgoing;
		int delayAfterNMessges;
		long delayByNMiliseconds;

		public PrivateHandler(Handler outgoing, int delayAfterNMessges, long delayByNMiliseconds)
		{
			this.outgoing = outgoing;
			this.delayAfterNMessges = delayAfterNMessges;
			this.delayByNMiliseconds = delayByNMiliseconds;
		}

		@Override
		void handleMessage(Message m)
		{
			if (messages.size() > delayAfterNMessges)
			{
				try
				{
					Thread.sleep(delayByNMiliseconds);
				} catch (InterruptedException ie)
				{
					//logger.warn("PrivateHandler delay interrupted.");
				}
			}
			outgoing.postMessage(m);
		}
	}

	/**
	 * handle a channel message, this is likely to be a
	 * pass-through straight to the outgoing handler.
	 */
	static class ChannelHandler extends Handler
	{

		Handler outgoing;

		public ChannelHandler(Handler outgoing)
		{
			this.outgoing = outgoing;
		}

		@Override
		void handleMessage(Message m)
		{
			outgoing.postMessage(m);
		}
	}

	/**
	 * defines what sort of message this is.
	 */
	static enum MessageType
	{
		// We need some way to unblock the threads, so the messageType: "END"
		// is there so we can post a message to the queue and have the
		// handler threads process it.

		CHANNEL, PRIVATE, END;
	}

	/**
	 * Contains a message and it's type.
	 */
	static class Message
	{

		String data;
		MessageType type;
		String target;

		public Message(String target, String data, MessageType type)
		{
			this.data = data;
			this.type = type;
			this.target = target;
		}

		public String getData()
		{
			return data;
		}

		public String getTarget()
		{
			return target;
		}

		public MessageType getType()
		{
			return type;
		}
	}
}

