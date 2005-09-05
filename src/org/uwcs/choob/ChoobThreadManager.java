package org.uwcs.choob;

import java.util.concurrent.*;

/**
 * Manager for queueing new tasks and stuff
 * @author bucko
 */

class ChoobThreadManager {
	private static ExecutorService exe;

	static void initialise()
	{
		if ( exe == null )
		{
			// TODO make a neater work queue that limits threads per plugin etc.
			exe = new ThreadPoolExecutor(5, 20, (long)60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10, true));
		}
	}

	static void queueTask(ChoobTask task)
	{
		exe.execute(task);
	}
}
