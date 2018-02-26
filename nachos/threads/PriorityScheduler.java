package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A scheduler that chooses threads based on their priorities.
 * 
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 * 
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 * 
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 * 
	 * @param transferPriority <tt>true</tt> if this queue should transfer
	 * priority from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			ret = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			ret = false;
		else
			setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;

	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;

	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	 protected class PriorityQueue extends ThreadQueue {

	        PriorityQueue(boolean transferPriority) {
	          // instantiate a queue to hold the threads waiting to access the resource
	            this.waitingList = new LinkedList<ThreadState>();
	            this.transferPriority = transferPriority;
	        }

	        public void waitForAccess(KThread thread) {
	        	
	            Lib.assertTrue(Machine.interrupt().disabled());
	            // add this thread to the waitingList
	            this.waitingList.add(getThreadState(thread));
	            // add this queue to resources the thread is waiting for
	            getThreadState(thread).waitForAccess(this);
	        }

	        public void acquire(KThread thread) {
	            Lib.assertTrue(Machine.interrupt().disabled());
	            if (this.resourceOwner != null) {
	                this.resourceOwner.release(this);
	            }
	            // if the resource is free, set this thread as the resource owner 
	            this.resourceOwner = getThreadState(thread);
	            getThreadState(thread).acquire(this);
	        }

	        public KThread nextThread() {
	            Lib.assertTrue(Machine.interrupt().disabled());
	            final ThreadState nextThreadToRun = this.pickNextThread();
	            if (nextThreadToRun == null) return null;
	            this.waitingList.remove(nextThreadToRun);
	            this.acquire(nextThreadToRun.getThread());
	            return nextThreadToRun.getThread();
	        }

	  

	        /**
	         * Return the next thread that <tt>nextThread()</tt> would return,
	         * without modifying the state of this queue.
	         *
	         * @return the next thread that <tt>nextThread()</tt> would
	         *         return.
	         */
	        protected ThreadState pickNextThread() {
	            int maxPriority = priorityMinimum;
	            ThreadState nextThread = null;
	            // iterate through threads in the waitingList
	            for (final ThreadState threadI : this.waitingList) {
	            	// check for max
	                if (nextThread == null || (threadI.getEffectivePriority() > maxPriority)) {
	                    nextThread = threadI;
	                    maxPriority = threadI.getEffectivePriority();
	                }
	            }
	            return nextThread;
	        }

	        /**
	         * This method returns the effectivePriority of this PriorityQueue.
	         * The return value is cached for as long as possible. If the cached value
	         * has been invalidated, this method will spawn a series of mutually
	         * recursive calls needed to recalculate effectivePriorities across the
	         * entire resource graph.
	         * @return
	         */
	        public int getEffectivePriority() {
	            if (!this.transferPriority) {
	                return priorityMinimum;
	            } else if (this.priorityUpdate) {
	                // Recalculate effective priorities
	                this.effectivePriority = priorityMinimum;
	                for (final ThreadState threadI : this.waitingList) {
	                    this.effectivePriority = Math.max(this.effectivePriority, threadI.getEffectivePriority());
	                }
	                this.priorityUpdate = false;
	            }
	            return effectivePriority;
	        }

	        public void print() {
	            Lib.assertTrue(Machine.interrupt().disabled());
	            for (final ThreadState threadI : this.waitingList) {
	                System.out.println(threadI.getEffectivePriority());
	            }
	        }

	        private void updateCachedPriority() {
	        	// no need to update priority
	            if (!this.transferPriority) return;

	            this.priorityUpdate = true;

	            if (this.resourceOwner != null) {
	                resourceOwner.updateCachedPriority();
	            }
	        }

	       
	        protected final List<ThreadState> waitingList;
	       
	        protected ThreadState resourceOwner = null;
	        
	        protected int effectivePriority = priorityMinimum;
	        
	        protected boolean priorityUpdate = false;
	        /**
	         * <tt>true</tt> if this queue should transfer priority from waiting
	         * threads to the owning thread.
	         */
	        public boolean transferPriority;
	    }

	    /**
	     * The scheduling state of a thread. This should include the thread's
	     * priority, its effective priority, any objects it owns, and the queue
	     * it's waiting for, if any.
	     *
	     * @see nachos.threads.KThread#schedulingState
	     */
	    protected class ThreadState {
	        /**
	         * Allocate a new <tt>ThreadState</tt> object and associate it with the
	         * specified thread.
	         *
	         * @param thread the thread this state belongs to.
	         */
	        public ThreadState(KThread thread) {
	        	
	            this.thread = thread;
	            //instantiate two lists for resource
	            this.owenedResource = new LinkedList<PriorityQueue>();
	            this.waitingForResource = new LinkedList<PriorityQueue>();

	            setPriority(priorityDefault);

	        }

	        /**
	         * Return the priority of the associated thread.
	         *
	         * @return the priority of the associated thread.
	         */
	        public int getPriority() {
	            return priority;
	        }

	        /**
	         * Return the effective priority of the associated thread.
	         *
	         * @return the effective priority of the associated thread.
	         */
	        public int getEffectivePriority() {
	        	// if no owned resource, priority did'n change
	            if (this.owenedResource.isEmpty()) {
	                return this.getPriority();
	            } else if (this.priorityUpdate) {
	                this.effectivePriority = this.getPriority();
	                for (final PriorityQueue r : this.owenedResource) {
	                    this.effectivePriority = Math.max(this.effectivePriority, r.getEffectivePriority());
	                }
	                this.priorityUpdate = false;
	            }
	            return this.effectivePriority;
	        }

	        /**
	         * Set the priority of the associated thread to the specified value.
	         *
	         * @param priority the new priority.
	         */
	        public void setPriority(int priority) {
	            if (this.priority == priority)
	                return;
	            this.priority = priority;
	            // iterate through all resources waiting for and indicate change of priority might happen
	            for (final PriorityQueue r : waitingForResource) {
	                r.updateCachedPriority();
	            }
	        }

	        /**
	         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	         * the associated thread) is invoked on the specified priority queue.
	         * The associated thread is therefore waiting for access to the
	         * resource guarded by <tt>waitQueue</tt>. This method is only called
	         * if the associated thread cannot immediately obtain access.
	         *
	         * @param waitQueue the queue that the associated thread is
	         *                  now waiting on.
	         * @see nachos.threads.ThreadQueue#waitForAccess
	         */
	        public void waitForAccess(PriorityQueue waitQueue) {
	        	// update both lists of resource
	            this.waitingForResource.add(waitQueue);
	            this.owenedResource.remove(waitQueue);
	            // indicate change of priority might happen
	            waitQueue.updateCachedPriority();
	        }

	        /**
	         * Called when the associated thread has acquired access to whatever is
	         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	         * <tt>thread</tt> is the associated thread), or as a result of
	         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	         *
	         * @see nachos.threads.ThreadQueue#acquire
	         * @see nachos.threads.ThreadQueue#nextThread
	         */
	        public void acquire(PriorityQueue waitQueue) {
	            this.owenedResource.add(waitQueue);
	            this.waitingForResource.remove(waitQueue);
	            this.updateCachedPriority();
	        }

	        /**
	         * Called when the associated thread has relinquished access to whatever
	         * is guarded by waitQueue.
	          * @param waitQueue The waitQueue corresponding to the relinquished resource.
	         */
	        public void release(PriorityQueue waitQueue) {
	            this.owenedResource.remove(waitQueue);
	            this.updateCachedPriority();
	        }

	        public KThread getThread() {
	            return thread;
	        }

	        private void updateCachedPriority() {
	            if (this.priorityUpdate) return;
	            this.priorityUpdate = true;
	            for (final PriorityQueue r : this.waitingForResource) {
	                r.updateCachedPriority();
	            }
	        }


	        /**
	         * The thread with which this object is associated.
	         */
	        protected KThread thread;
	        /**
	         * The priority of the associated thread.
	         */
	        protected int priority;

	 
	        protected boolean priorityUpdate = false;
	        
	        protected int effectivePriority = priorityMinimum;
	       
	        protected final List<PriorityQueue> owenedResource;
	       
	        protected final List<PriorityQueue> waitingForResource;
	    }
}
