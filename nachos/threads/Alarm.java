package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.*;
import nachos.threads.Alarm.WaitTime;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
		alarmPQ = new PriorityQueue<WaitTime>();
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {

		boolean intStatus = Machine.interrupt().disable();
		
		while(!alarmPQ.isEmpty() && (alarmPQ.peek().wakeTime <= Machine.timer().getTime())){
			WaitTime thread = alarmPQ.remove();
			thread.waitThread.ready();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		boolean intStatus = Machine.interrupt().disable();
		long wakeTime = Machine.timer().getTime() + x;

		WaitTime thread = new WaitTime(KThread.currentThread(), wakeTime);
		alarmPQ.add(thread);
		KThread.sleep();

		Machine.interrupt().restore(intStatus);
	}
	 public class WaitTime implements Comparable<WaitTime> {
		 /**
	    	  * Allocate a new WaitTime with a KThread and its wakeTime 	  
	   	  *  
	    	  */

		public WaitTime(KThread waitThread, long wakeTime){
			this.waitThread = waitThread;
			this.wakeTime = wakeTime;
		}
		
		 /**
	    	  * Use Java's Long compareTo method to compare the wakeTimes of "this" WaitTime  
	   	  *  and another WaitTime object
	    	  */

		public int compareTo(WaitTime other){
			return (new Long(this.wakeTime).compareTo(new Long(other.wakeTime)));
		}

		//Instance variables for WaitTime class
		private KThread waitThread;
		private long wakeTime;
	     }
	private PriorityQueue<WaitTime> alarmPQ;

}
