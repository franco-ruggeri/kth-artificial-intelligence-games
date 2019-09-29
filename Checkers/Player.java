import java.util.*;

public class Player {
	private int whoAmI;
	private Deadline deadline;
	private int maxDepth;		// current iteration (iterative deepening)
	private boolean timeout;	// set to true when deadline is almost reached
	private Vector<GameState> bestPath = new Vector<>();		// for move ordering
	
	private static final long MARGIN_DEADLINE = (long) (5*1e7);	// 50 ms (5000000 ns) of margin
	private static final long TIME_TO_RETURN = (long) 1e5;		// 0.1 ms (100000 ns) to return 1 level up in recursion
	
    /**
     * Performs a move
     *
     * @param pState
     *            the current state of the board
     * @param pDue
     *            time before which we must have returned
     * @return the next state the board is in after our move
     */
    public GameState play(final GameState pState, final Deadline pDue) {
        whoAmI = pState.getNextPlayer();
        deadline = pDue;
        return alphabeta(pState);
    }
    
    private GameState alphabeta(GameState state) {
    	Vector<GameState> nextStates = new Vector<>();
    	int v = Integer.MIN_VALUE;
    	GameState choice = null;
    	
    	// fill next states
    	state.findPossibleMoves(nextStates);
    	
    	// no possible moves, pass
    	if (nextStates.isEmpty())
    		return new GameState(state, new Move());

    	// iterative deepening
    	maxDepth = 0;
    	timeout = false;
    	bestPath.clear();
    	while (!timeout) {
    		// save result of the previous completed iteration
    		if (!bestPath.isEmpty())
    			choice = bestPath.lastElement();
    		
    		// prepare new iteration
    		maxDepth++;
    		bestPath.add(0, null);	// shift other elements to right, new position for the new step in depth
    		
    		// move ordering
    		if (maxDepth > 1)
    			moveOrdering(nextStates, maxDepth);
    		
    		// find action maximizing the "utility"
        	for (int i=0; i<nextStates.size() && !timeout; i++) {
        		GameState s = nextStates.elementAt(i);
        		int tmp = alphabetaR(s, maxDepth-1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        		if (tmp > v) {
        			v = tmp;	// max
        			bestPath.setElementAt(s, maxDepth-1);	// argmax
        		}
        	}
    	}
    	
    	return choice;
    }
    
    private int alphabetaR(GameState state, int depth, int alpha, int beta) {
    	int player = state.getNextPlayer();
    	Vector<GameState> nextStates = new Vector<>();
    	int v;
    	
    	// check deadline
    	if (timeout(maxDepth - depth)) {
    		timeout = true;
    		return 0;	// end search
    	}
    	
    	// fill next states
    	state.findPossibleMoves(nextStates);
    	
    	// cutoff test
    	if (depth == 0 || nextStates.isEmpty())
    		return evaluate(state);
    	
    	// move ordering
    	if (depth > 1)
    		moveOrdering(nextStates, depth);
    	
    	// it's me, I look for the maximum
    	if (player == whoAmI) {
    		v = Integer.MIN_VALUE;
    		for (GameState s : nextStates) {
    			int tmp = alphabetaR(s, depth-1, alpha, beta);
    			if (timeout)
    				return 0;	// end search
    			if (tmp > v) {
    				v = tmp;
    				// save best move for move ordering at the next iteration
    				bestPath.setElementAt(s, depth-1);
    			}
    			if (tmp > alpha)
    				alpha = tmp;
    			if (beta <= alpha)
    				break;
    		}
    	}
    	// it's the opponent, he looks for the minimum
    	else {
    		v = Integer.MAX_VALUE;
    		for (GameState s : nextStates) {
    			int tmp = alphabetaR(s, depth-1, alpha, beta);
    			if (timeout)
    				return 0;	// end search
    			if (tmp < v) {
    				v = tmp;
    				// save best move for move ordering at the next iteration
    				bestPath.setElementAt(s, depth-1);
    			}
    			if (tmp < beta)
    				beta = tmp;
    			if (beta <= alpha)
    				break;
    		}
    	}
    	
    	return v;
    }
    
    private int evaluate(GameState state) {
    	// TODO: improve considering jumps
    	
    	int globalSum = 0;
    	int partialSum;
    	int i;
    	int pos;
    	
    	// terminal state, the result is certain
    	if (state.isEOG()) {
    		if (isWin(state))
    			return Integer.MAX_VALUE;
    		else if (isLoss(state))
    			return Integer.MIN_VALUE;
    		else
    			return 0;	// draw
    	}
    	
    	partialSum = 0;
    	for (i = 0; i < GameState.NUMBER_OF_SQUARES; i++) {
    		pos = state.get(i);
    		if ((pos & whoAmI) != 0) {
    			if ((pos & Constants.CELL_KING) != 0) {
    				partialSum += 2;
    			} else {
    				partialSum += 1; //GameState.cellToRow(i);
//    				partialSum *= Math.abs(partialSum);
    			}
    		} else {
    			if ((pos & Constants.CELL_KING) != 0) {
    				partialSum -= 2;
    			} else {    				
    				partialSum -= 1; //7 - GameState.cellToRow(i);
//    				partialSum *= Math.abs(partialSum);
    			}
    		}
    	}
    	globalSum += partialSum;
    	
    	return globalSum;
    }
    
    private boolean isWin(GameState state) {
    	return (whoAmI == Constants.CELL_RED && state.isRedWin()) ||
    			(whoAmI == Constants.CELL_WHITE && state.isWhiteWin());
    }
    
    private boolean isLoss(GameState state) {
    	return (whoAmI == Constants.CELL_RED && state.isWhiteWin()) ||
    			(whoAmI == Constants.CELL_WHITE && state.isRedWin());
    }
    
    private boolean timeout(int depth) {
    	return deadline.timeUntil() <= MARGIN_DEADLINE + TIME_TO_RETURN*depth;
    }
    
    private void moveOrdering(Vector<GameState> nextStates, int depth) {
    	/*
		 * Move ordering:
		 * 1. use the best move of the previous iteration as first
		 * 2a. order the other moves this way: becoming king, jump, normal
		 * 2b. shuffle the other moves (random, O(b^3*m/4), see book)
		 */
    	GameState bestNextState = bestPath.elementAt(depth-1);
    	Collections.sort(nextStates, (s1, s2) -> {
			Move m1 = s1.getMove();
			Move m2 = s2.getMove();
			boolean isBecomingKing1 = isBecomingKing(s1);
			boolean isBecomingKing2 = isBecomingKing(s2);
			int result;
			
			// 1
			if (s1.toMessage().equals(bestNextState.toMessage()))
				result = 1;
			else if (s2.toMessage().equals(bestNextState.toMessage()))
				result = -1;
			// 2a
			else if (isBecomingKing1 && !isBecomingKing2)
				result = 1;
			else if (isBecomingKing2 && !isBecomingKing1)
				result = -1;
			else if (m1.isJump() && m2.isNormal())
				result = 1;
			else if (m1.isNormal() && m2.isJump())
				result = -1;
			else
				result = 0;
			// 2b
//			else
//				result = (int) (Math.random() * 2.0 - 1);
		
			return result;
		});
    }
    
    private boolean isBecomingKing(GameState state) {
    	Move m = state.getMove();
    	return (state.get(m.at(m.length()-1)) & Constants.CELL_KING) != 0;
    }
}
