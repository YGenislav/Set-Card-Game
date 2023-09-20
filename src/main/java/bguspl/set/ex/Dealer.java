package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Collections;
import java.util.LinkedList;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    
    private List<Integer> playersWithSet= new LinkedList<Integer>();

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        
        for (int i=0; i<env.config.players; i++){
            Thread playerThread=new Thread(players[i], "player "+i);
            playerThread.start();
        }
        table.updateDealer(this, Thread.currentThread());
        while (!shouldFinish()) {
            placeCardsOnTable();
            reshuffleTime = System.currentTimeMillis()+env.config.turnTimeoutMillis+1000;
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        for (int i=env.config.players-1; i>=0; i--){
            players[i].freezeUntilChecked(); //terminating threads in opposite order to their creation
            players[i].terminate();
        }
        announceWinners();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate=true;
    }
    
    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }
    
    public synchronized void AddToChecklist (int id) {
        if (reshuffleTime>System.currentTimeMillis()){
    	players[id].freezeUntilChecked();
    	playersWithSet.add(id);
        }
        else for (int i=0; i<env.config.tableSize; i++){
            table.removeToken(id, i);
        }
    }
    
    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        
        // If a player has a legal set on the table, delete the cards from the table
        boolean find = false;
        while (!playersWithSet.isEmpty() && find == false){
        	int playerID=playersWithSet.remove(0);
            int numberOfToken = 0; 
            int [] cardsWithToken=new int [3];
            for (int j=0; j < 3; j++){
                int currentSlot=table.getTokenArray(playerID,j);
                if(currentSlot != -1){
                    numberOfToken ++;
                    cardsWithToken[j] = table.getSlottoCard(table.getTokenArray(playerID,j));
                    table.removeToken(playerID, currentSlot);
                }
            }

            if(numberOfToken == 3) {
            	if (env.util.testSet(cardsWithToken) == true){
                players[playerID].toPoint();
                for (int j=0; j < 3; j++) {
                	int slot=table.getCardtoSlot(cardsWithToken[j]);
                    table.removeCard(slot);
                }
                find = true;
                }
            	else players[playerID].toPenalty();
            }
        }
    }
    
    
    
    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
            for (int i=0; deck.size() > 0 && i < env.config.tableSize && table.countCards() < env.config.tableSize; i++){
                if (table.getSlottoCard(i) == -1){
                    Collections.shuffle(deck);
                    table.placeCard(deck.remove(0), i);
                }
            }
        }
      
    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
    	try {
			Thread.sleep(20);
		} catch (InterruptedException e) {}
    }
    
    
     // Reset and/or update the countdown and the countdown display.


     private  void updateTimerDisplay(boolean reset) {
     	if (reset){
     	reshuffleTime+=env.config.turnTimeoutMillis; //updates the time for the next reshuffle to be the current time plus the time between reshuffles, if the reset is true        
         }
        long timeLeft=reshuffleTime-System.currentTimeMillis();
         if (timeLeft>env.config.turnTimeoutWarningMillis) env.ui.setCountdown(timeLeft, false);
         else env.ui.setCountdown(timeLeft, true);
        
     } 

    
     // Returns all the cards from the table to the deck.
     
    private void removeAllCardsFromTable() {
    
        for (int i=0; i < env.config.tableSize; i++){
            int curr=table.getSlottoCard(i);
            if (curr!=-1){
                deck.add(curr);
                table.removeCard(i);
            }
        }    
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
            List<Integer> winnersIDList= new LinkedList<Integer>();
            int highestScore=0;
            for (int i=0; i<players.length; i++)
            {
            	if(players[i].score()>=highestScore){
                    if(players[i].score()==highestScore)
                    winnersIDList.add(i);
                    else {
                        winnersIDList.clear();
                        winnersIDList.add(i);
                        highestScore=players[i].score();
                }
            }
        }
        int[] winnersID=new int [winnersIDList.size()];
        for (int i=0; i<winnersID.length; i++){
            winnersID[i]=winnersIDList.get(i);
        }
            env.ui.announceWinner(winnersID);
            try {
                Thread.sleep(env.config.endGamePauseMillies);
            } catch (InterruptedException e) {}
            
    }
}
