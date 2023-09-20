package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)
    
    private Dealer dealer;
    
    private Thread dealerThread;

    private Integer[][] tokenArray;

    private Object cardTokenLock;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        cardTokenLock=new Object();
        tokenArray = new Integer[env.config.players][3];
        for (int i=0; i<env.config.players; i++) {
        	tokenArray[i][0]=-1;
        	tokenArray[i][1]=-1;
        	tokenArray[i][2]=-1;
        }
    }
    
    public void updateDealer(Dealer dealer, Thread dealerThread) {
    	this.dealer=dealer;
    	this.dealerThread=dealerThread;
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        synchronized(cardTokenLock){
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        env.ui.removeCard(slot);
        int card=slotToCard[slot];
        slotToCard[slot] = null;
        cardToSlot[card] = null;
        for (int i=0;i<tokenArray.length;i++) {
            
        	for (int j=0; j<3; j++)
        	if (tokenArray[i][j]==slot) {
        		tokenArray[i][j]=-1;
        		env.ui.removeToken(i, slot);
            }
        }
    }
    }
    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        synchronized(cardTokenLock){
        boolean placedToken=false; 
        for (int i=0; i < 3 && placedToken==false; i++){
            if (tokenArray[player][i] == -1 && slotToCard[slot]!=null) {
                tokenArray[player][i] = slot;
                placedToken=true; 
                env.ui.placeToken(player, slot);
                if (i==2)
                {
                	dealerThread.interrupt(); //wake up where have a ligal set
                	dealer.AddToChecklist(player);
                }
                }
            } 
    }
}

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        for (int i=0; i < 3; i++){
            if (tokenArray[player][i] == slot) {
                tokenArray[player][i] = -1;
                env.ui.removeToken(player, slot); 
                return true;
            }
        }

        return false;
    }
    
    public void setCardtoSlot (int card, int slot) {
        synchronized(cardTokenLock){
    	if (slot==-1) cardToSlot [card]=null;
    	else cardToSlot [card]=slot;
        }
    }
    
    public Integer getCardtoSlot (int card) {
        synchronized(cardTokenLock){
    	if (cardToSlot[card]==null) return -1;
    	return cardToSlot[card];
        }
    }
    
    public void setSlottoCard (int slot, int card) {
        synchronized(cardTokenLock){
    	if (card==-1) slotToCard [slot]=null;
    	else slotToCard [slot]=card;
        }
    }
    
    public Integer getSlottoCard (int slot) {
        synchronized(cardTokenLock){
    	if (slotToCard[slot]==null) return -1;
    		return slotToCard[slot];
        }
    }
    
    public void setTokenArray (int player, int num, int value) {
        tokenArray [player][num]=value;
    }
    
    public int getTokenArray (int player, int num) {
        return tokenArray [player][num];
    }
}
