package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Queue;
import java.util.LinkedList;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;


    public Queue<Integer> playersQueue;
    
    private boolean movesAllowed=true;

    private boolean toPenalty=false;

    private boolean toPoint=false;

    private Object movesAllowedLock;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        terminate=false;
        movesAllowedLock=new Object();
        playersQueue = new LinkedList<Integer>();

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            if (toPoint) point();
            if (toPenalty) penalty();
            //System.out.println("player thread still running, movesAllowed:"+movesAllowed);
            if (movesAllowed) try {
                Thread.sleep(env.config.penaltyFreezeMillis+env.config.pointFreezeMillis+50);//added 26.12
            } catch (InterruptedException e) {}
            else {try {//failsafe
                Thread.sleep(env.config.penaltyFreezeMillis+env.config.pointFreezeMillis+50);
            } catch (InterruptedException e) {}
            movesAllowed=true;
        }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
            	while (movesAllowed) {
                int slot = (int) (Math.random() * env.config.tableSize);
            	 keyPressed(slot);
                }
                try {
                    synchronized (this) { Thread.sleep(env.config.penaltyFreezeMillis+env.config.pointFreezeMillis+50); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate=true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public  void keyPressed(int slot) {
        if (movesAllowed) {
        boolean slotExists=false;
        for(int i=0; i<3 && slotExists == false; i++){
            if(table.getTokenArray(id,i) == slot){
                slotExists = true;
            }
        } 
            if (slotExists == true){
                    table.removeToken(id, slot);
            }
            else{
                table.placeToken(id, slot);
            }
        }

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        synchronized(movesAllowedLock){
        env.ui.setScore(id, ++score);
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
    	try {
			Thread.sleep(env.config.pointFreezeMillis);
		} catch (InterruptedException e) {}
    	env.ui.setFreeze(id,0); //unfreezes the player on the UI
        toPoint=false;
        movesAllowed=true;
        if (!human) aiThread.interrupt();
    }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        synchronized(movesAllowedLock){
    	env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
    	try {
			Thread.sleep(env.config.penaltyFreezeMillis);
		} catch (InterruptedException e) {}
    	env.ui.setFreeze(id, 0); //unfreezes the player on the UI
        toPenalty=false;
        movesAllowed=true;
        if (!human) aiThread.interrupt();
    }
    }
    
    public void freezeUntilChecked() {
        synchronized(movesAllowedLock){
        	movesAllowed=false;
        }
        }

    public int score() {
        return score;
    }
    
    public void toPoint(){
        toPoint=true;
        playerThread.interrupt();
    }

    
    public void toPenalty(){
        toPenalty=true;
        playerThread.interrupt();
    }
    
    
}
