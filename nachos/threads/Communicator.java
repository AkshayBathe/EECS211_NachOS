package nachos.threads;


import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
	handShakeInProgress = false;
	waitForListeningQueueSize = 0;
	lock = new Lock();
	waitForHandShaking = new Condition2(lock);
	waitForSpeaking = new Condition2(lock);
	waitForListening = new Condition2(lock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     * <p/>
     * <p/>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param    word    the integer to transfer.
     */
    public void speak(int word) {
	lock.acquire();
	while(handShakeInProgress){
		waitForSpeaking.sleep();
	}
	
	handShakeInProgress = true;
        this.message = word;
	
	while(waitForListeningQueueSize == 0){
		waitForHandShaking.sleep();
	}
	
	waitForListening.wake();
	waitForHandShaking.sleep();
	handShakeInProgress = false;
	waitForSpeaking.wake();
	lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        lock.acquire();
	waitForListeningQueueSize++;
	
	if(waitForListeningQueueSize == 1 && handShakeInProgress){
		waitForHandShaking.wake();
	}
	
	waitForListening.sleep();
	waitForHandShaking.wake();
	waitForListeningQueueSize--;
        int myMessage = this.message;
	lock.release();
	return myMessage;
    }

    public int getQueueSize(){
	return waitForListeningQueueSize;
    }
  
    

    private boolean handShakeInProgress;
    private int waitForListeningQueueSize;
    private int message;
    private Lock lock;
    private Condition2 waitForHandShaking;
    private Condition2 waitForSpeaking;
    private Condition2 waitForListening;
    
}