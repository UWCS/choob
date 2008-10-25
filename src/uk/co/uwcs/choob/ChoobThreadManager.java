package uk.co.uwcs.choob;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobPermission;

/**
 * Manager for queueing new tasks and stuff
 * @author bucko
 */

public final class ChoobThreadManager extends ThreadPoolExecutor {
	private static ChoobThreadManager exe;
	private final Modules mods;
	private final long pwoSetupTime;
	private final Map<String,PluginWaitObject> waitObjects;
	//private Map<ChoobTask,String> runningTasks;
	private final Map<String,BlockingQueue<ChoobTask>> queues;

	// Time during which the PluginWaitObjects are expired immediately. After
	// this time, they only expire after PLUGIN_WAIT_OBJECT_EXIRES time.
	private static final long PLUGIN_WAIT_OBJECT_SETUP_TIME =  1 * 60 * 1000; //  1 minute
	private static final long PLUGIN_WAIT_OBJECT_EXIRES     = 15 * 60 * 1000; // 15 minutes

	private final class PluginWaitObject
	{
		public int limit;
		public Semaphore sem;
		public long expires;

		public PluginWaitObject(final int limit)
		{
			this.limit = limit;
			this.sem = new Semaphore(limit);
			this.expires = System.currentTimeMillis() + PLUGIN_WAIT_OBJECT_EXIRES;
		}
	}

	private ChoobThreadManager(final Modules mods)
	{
		super(5, 20, 60l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		setThreadFactory( new ThreadFactory() {
			int count = 0;
			public Thread newThread(final Runnable r) {
				final ChoobThread thread = new ChoobThread(r, "choob-" + ++count);
				return thread;
			}
		});
		this.mods = mods;
		this.pwoSetupTime = System.currentTimeMillis() + PLUGIN_WAIT_OBJECT_SETUP_TIME;
		this.waitObjects = new HashMap<String,PluginWaitObject>();
		//this.runningTasks = new HashMap<ChoobTask,String>();
		this.queues = new HashMap<String,BlockingQueue<ChoobTask>>();
	}

	@Override
	protected void afterExecute(final Runnable runTask, final Throwable thrown)
	{
		super.afterExecute(runTask, thrown);

		final ChoobTask task = (ChoobTask) runTask;
		final String pluginName = task.getPluginName();

		//synchronized(runningTasks) {
		//	runningTasks.remove(task);
		//}

		// Was it a system task?
		if (pluginName == null)
			return;

		ChoobThread.clearPluginsStatic(); // Make sure stack is clean

		// Before we finish up, do we have more for this plugin?
		final BlockingQueue<ChoobTask> queue = getQueue(pluginName);
		final ChoobTask next = queue.poll();
		if (next != null)
		{
			// If so, just queue that. Don't relinquish the semaphore.
			exe.execute(next);
		}
		else
		{
			synchronized(waitObjects)
			{
				// If not, let someone else have a chance.
				final PluginWaitObject waitObject = getWaitObject(pluginName);

				waitObject.sem.release();

				// Remove the wait object, if we're not using it.
				if (waitObject.sem.availablePermits() == waitObject.limit &&
					(waitObject.expires < System.currentTimeMillis() ||
					 this.pwoSetupTime > System.currentTimeMillis()))
				{
					waitObjects.remove(pluginName.toLowerCase());
				}
			}
		}
	}

	@Override
	protected void beforeExecute(final Thread thread, final Runnable task)
	{
		// Queue the plugin up onto the stack
		final String pluginName = ((ChoobTask)task).getPluginName();

		//synchronized(runningTasks) {
		//	String sf = ((ChoobTask)task).getSystemFunction();
		//	runningTasks.put((ChoobTask)task, (pluginName == null ? "S:" + sf : "P:" + pluginName + (sf != null ? ":S:" + sf : "")));
		//}

		// System task?
		if (pluginName == null)
			return;

		((ChoobThread)thread).pushPlugin(pluginName);
	}

	static void initialise(final Modules mods)
	{
		if (exe == null)
			exe = new ChoobThreadManager(mods);
	}

	private PluginWaitObject getWaitObject(final String pluginName)
	{
		// This needs synchronization.
		synchronized(waitObjects)
		{
			PluginWaitObject ret = waitObjects.get(pluginName.toLowerCase());
			if (ret == null)
			{
				final int limit = mods.plugin.getConcurrencyLimit(pluginName);
				ret = new PluginWaitObject(limit);
				waitObjects.put(pluginName.toLowerCase(), ret);
			}
			return ret;
		}
	}

	// This needs synchronization.
	private synchronized BlockingQueue<ChoobTask> getQueue(final String pluginName)
	{
		BlockingQueue<ChoobTask> ret = queues.get(pluginName.toLowerCase());
		if (ret == null)
		{
			// When Where spams it's WHO requests, it needs to have a reply line for every user
			// in the channels queued.
			ret = new ArrayBlockingQueue<ChoobTask>(1000);
			queues.put(pluginName.toLowerCase(), ret);
		}
		return ret;
	}

	public static void queueTask(final ChoobTask task) throws RejectedExecutionException
	{
		java.security.AccessController.checkPermission(new ChoobPermission("task.queue"));
		exe.queue(task);
	}

	private void queue(final ChoobTask task) throws RejectedExecutionException
	{
		//synchronized(runningTasks) {
		//	System.out.print("ChoobThreadManager.beforeExecute: pool usage = " + getActiveCount() + "/" + getPoolSize());
		//	for (String n : runningTasks.values()) {
		//		System.out.print(", " + n);
		//	}
		//	System.out.println();
		//}

		final String pluginName = task.getPluginName();
		if (pluginName == null)
		{
			// system task!
			execute(task);
			return;
		}

		boolean acquireOK = false;
		synchronized(waitObjects)
		{
			final PluginWaitObject waitObject = getWaitObject(pluginName);
			acquireOK = waitObject.sem.tryAcquire();
		}
		if (acquireOK)
		{
			execute(task);
		}
		else
		{
			// Couldn't get the semaphore lock...
			final BlockingQueue<ChoobTask> queue = getQueue(pluginName);

			// Attempt to queue it for later.
			if (!queue.offer(task))
				// And the queue is full. Time to pop.
				throw new RejectedExecutionException("Plugin " + pluginName + " has too many queued tasks!");
		}
	}
}
