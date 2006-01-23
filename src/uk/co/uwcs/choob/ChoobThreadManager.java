package uk.co.uwcs.choob;

import java.util.concurrent.*;
import uk.co.uwcs.choob.support.*;
import java.util.*;

/**
 * Manager for queueing new tasks and stuff
 * @author bucko
 */

public final class ChoobThreadManager extends ThreadPoolExecutor {
	private static ChoobThreadManager exe;
	//private Modules mods; // TODO - make use of this to get thread counts for plugins.
	private Map<String,Semaphore> waitObjects;
	private Map<String,BlockingQueue<ChoobTask>> queues;

	private ChoobThreadManager()
	{
		super(5, 20, (long)60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		setThreadFactory( new ThreadFactory() {
			int count = 0;
			public Thread newThread(Runnable r) {
				ChoobThread thread = new ChoobThread(r, "choob-" + ++count);
				return thread;
			}
		});
		this.waitObjects = new HashMap<String,Semaphore>();
		this.queues = new HashMap<String,BlockingQueue<ChoobTask>>();
	}

	protected void afterExecute(Runnable runTask, Throwable thrown)
	{
		super.afterExecute(runTask, thrown);

		ChoobTask task = (ChoobTask) runTask;
		String pluginName = task.getPluginName();

		// Was it a system task?
		if (pluginName == null)
			return;

		System.out.println("afterExecute (" + pluginName + "): " + ChoobThread.getPluginStack());
		
		ChoobThread.clearPluginsStatic(); // Make sure stack is clean

		// Before we finish up, do we have more for this plugin?
		BlockingQueue<ChoobTask> queue = getQueue(pluginName);
		ChoobTask next = queue.poll();
		if (next != null)
		{
			// If so, just queue that. Don't relinquish the semaphore.
			exe.execute(next);
			System.out.println("Queued plugin task for " + pluginName + " now runnable.");
		}
		else
		{
			// If not, let someone else have a chance.
			getWaitObject(pluginName).release();
		}
	}

	protected void beforeExecute(Thread thread, Runnable task)
	{
		// Queue the plugin up onto the stack
		String pluginName = ((ChoobTask)task).getPluginName();

		// System task?
		if (pluginName == null)
			return;

		((ChoobThread)thread).pushPlugin(pluginName);
		System.out.println("beforeExecute (" + pluginName + "): " + ChoobThread.getPluginStack());
	}

	static void initialise()
	{
		if ( exe == null )
			exe = new ChoobThreadManager();
	}

	// This needs synchronization.
	private synchronized Semaphore getWaitObject(String pluginName)
	{
		Semaphore ret = waitObjects.get(pluginName.toLowerCase());
		if (ret == null)
		{
			ret = new Semaphore(2);
			waitObjects.put(pluginName.toLowerCase(), ret);
		}
		return ret;
	}

	// This needs synchronization.
	private synchronized BlockingQueue<ChoobTask> getQueue(String pluginName)
	{
		BlockingQueue<ChoobTask> ret = queues.get(pluginName.toLowerCase());
		if (ret == null)
		{
			ret = new ArrayBlockingQueue<ChoobTask>(30);
			queues.put(pluginName.toLowerCase(), ret);
		}
		return ret;
	}

	public static void queueTask(ChoobTask task) throws RejectedExecutionException
	{
		java.security.AccessController.checkPermission(new ChoobPermission("task.queue"));
		exe.queue(task);
	}

	private void queue(ChoobTask task) throws RejectedExecutionException
	{
		String pluginName = task.getPluginName();
		if (pluginName == null)
		{
			// system task!
			execute(task);
			return;
		}
		System.out.println("queue (" + pluginName + "): " + ChoobThread.getPluginStack());
		
		Semaphore sem = getWaitObject(pluginName);
		if (sem.tryAcquire())
		{
			execute(task);
		}
		else
		{
			// Couldn't get the semaphore lock...
			BlockingQueue<ChoobTask> queue = getQueue(pluginName);

			// Attempt to queue it for later.
			if (!queue.offer(task))
				// And the queue is full. Time to pop.
				throw new RejectedExecutionException("Plugin " + pluginName + " has too many queued tasks!");
			System.out.println("Plugin task for " + pluginName + " queued.");
		}
	}
}
