/* Skeleton code copyright (C) 2008, 2022 Paul N. Hilfinger and the
 * Regents of the University of California.  Do not distribute this or any
 * derivative work without permission. */

package ataxx;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Formatter;

import java.util.function.Consumer;

import static ataxx.PieceColor.*;
import static ataxx.GameException.error;

/** An Ataxx board.   The squares are labeled by column (a char value between
 *  'a' - 2 and 'g' + 2) and row (a char value between '1' - 2 and '7'
 *  + 2) or by linearized index, an integer described below.  Values of
 *  the column outside 'a' and 'g' and of the row outside '1' to '7' denote
 *  two layers of border squares, which are always blocked.
 *  This artificial border (which is never actually printed) is a common
 *  trick that allows one to avoid testing for edge conditions.
 *  For example, to look at all the possible moves from a square, sq,
 *  on the normal board (i.e., not in the border region), one can simply
 *  look at all squares within two rows and columns of sq without worrying
 *  about going off the board. Since squares in the border region are
 *  blocked, the normal logic that prevents moving to a blocked square
 *  will apply.
 *
 *  For some purposes, it is useful to refer to squares using a single
 *  integer, which we call its "linearized index".  This is simply the
 *  number of the square in row-major order (counting from 0).
 *
 *  Moves on this board are denoted by Moves.
 *  @author Christian T. Bernard
 */
class Board {

    /**
     * Number of squares on a side of the board.
     */
    static final int SIDE = Move.SIDE;

    /**
     * Length of a side + an artificial 2-deep border region.
     * This is unrelated to a move that is an "extend".
     */
    static final int EXTENDED_SIDE = Move.EXTENDED_SIDE;

    /**
     * Number of consecutive non-extending moves before game ends.
     */
    static final int JUMP_LIMIT = 25;

    /**
     * A new, cleared board in the initial configuration.
     */
    Board() {
        _board = new PieceColor[EXTENDED_SIDE * EXTENDED_SIDE];
        _allMoves = new ArrayList<>();
        _numJumps = 0;
        _undoPieces = new Stack<>();
        _undoSquares = new Stack<>();
        _winner = null;
        setNotifier(NOP);
        clear();
    }

    /**
     * A board whose initial contents are copied from BOARD0, but whose
     * undo history is clear, and whose notifier does nothing.
     */
    Board(Board board0) {
        _board = board0._board.clone();
        _allMoves = (ArrayList<Move>) board0._allMoves.clone();
        _numJumps = board0._numJumps;
        _numPass = board0._numPass;
        _numRed = board0._numRed;
        _numBlue = board0._numBlue;
        _winner = board0.getWinner();
        _whoseMove = board0.whoseMove();
        _numPieces = board0._numPieces.clone();
        _undoPieces = (Stack) board0._undoPieces.clone();
        _undoSquares = (Stack) board0._undoSquares.clone();
        setNotifier(NOP);
    }

    /**
     * Return the linearized index of square COL ROW.
     */
    static int index(char col, char row) {
        return (row - '1' + 2) * EXTENDED_SIDE + (col - 'a' + 2);
    }

    /**
     * Return the linearized index of the square that is DC columns and DR
     * rows away from the square with index SQ.
     */
    static int neighbor(int sq, int dc, int dr) {
        return sq + dc + dr * EXTENDED_SIDE;
    }

    /**
     * Clear me to my starting state, with pieces in their initial
     * positions and no blocks.
     */
    void clear() {
        for (char col = 'a'; col < 'h'; col++) {
            for (char row = '1'; row <= '7'; row++) {
                unrecordedSet(col, row, EMPTY);
            }
        }
        unrecordedSet('a', '7', RED);
        unrecordedSet('g', '1', RED);
        unrecordedSet('a', '1', BLUE);
        unrecordedSet('g', '7', BLUE);
        _allMoves.clear();
        _whoseMove = RED;
        _numJumps = 0;
        _numPass = 0;
        _numRed = 2;
        _numBlue = 2;
        _numPieces[RED.ordinal()] = 2;
        _numPieces[BLUE.ordinal()] = 2;
        _winner = null;
        _undoSquares.empty();
        _undoPieces.empty();
        announce();

    }

    /**
     * Return the winner, if there is one yet, and otherwise null.  Returns
     * EMPTY in the case of a draw, which can happen as a result of there
     * having been MAX_JUMPS consecutive jumps without intervening extends,
     * or if neither player can move and both have the same number of pieces.
     */
    PieceColor getWinner() {
        if ((!canMove(RED) && !canMove(BLUE)) || _numJumps >= JUMP_LIMIT) {
            if (redPieces() > bluePieces()) {
                return RED;
            } else if (bluePieces() > redPieces()) {
                return BLUE;
            } else if (bluePieces() == redPieces()) {
                return EMPTY;
            }
        }
        return null;
    }

    /**
     * Return number of red pieces on the board.
     */
    int redPieces() {
        return numPieces(RED);
    }

    /**
     * Return number of blue pieces on the board.
     */
    int bluePieces() {
        return numPieces(BLUE);
    }

    /**
     * Return number of COLOR pieces on the board.
     */
    int numPieces(PieceColor color) {
        return _numPieces[color.ordinal()];
    }

    /**
     * Increment numPieces(COLOR) by K.
     */
    private void incrPieces(PieceColor color, int k) {
        _numPieces[color.ordinal()] += k;
    }

    /**
     * The current contents of square CR, where 'a'-2 <= C <= 'g'+2, and
     * '1'-2 <= R <= '7'+2.  Squares outside the range a1-g7 are all
     * BLOCKED.  Returns the same value as get(index(C, R)).
     */
    PieceColor get(char c, char r) {
        return _board[index(c, r)];
    }

    /**
     * Return the current contents of square with linearized index SQ.
     */
    PieceColor get(int sq) {
        return _board[sq];
    }

    /**
     * Set get(C, R) to V, where 'a' <= C <= 'g', and
     * '1' <= R <= '7'. This operation is undoable.
     */
    private void set(char c, char r, PieceColor v) {
        set(index(c, r), v);
    }

    /**
     * Set square with linearized index SQ to V.  This operation is
     * undoable.
     */
    private void set(int sq, PieceColor v) {
        addUndo(sq);
        _board[sq] = v;
    }

    /**
     * Set square at C R to V (not undoable). This is used for changing
     * contents of the board without updating the undo stacks.
     */
    private void unrecordedSet(char c, char r, PieceColor v) {
        _board[index(c, r)] = v;
    }

    /**
     * Set square at linearized index SQ to V (not undoable). This is used
     * for changing contents of the board without updating the undo stacks.
     */
    private void unrecordedSet(int sq, PieceColor v) {
        _board[sq] = v;
    }

    /**
     * Return true iff MOVE is legal on the current board.
     */
    boolean legalMove(Move move) {
        if (move != null) {
            if (move.isPass()) {
                return !canMove(whoseMove());
            }
            if (get(move.fromIndex()) != whoseMove()) {
                return false;
            }
            if (!(move.isExtend() || move.isJump())) {
                return false;
            }
            if (get(move.toIndex()) != EMPTY) {
                return false;
            }
            if (_winner != null) {
                return false;
            }

        } else {
            return false;
        }
        return true;
    }

    /**
     * Return true iff C0 R0 - C1 R1 is legal on the current board.
     */
    boolean legalMove(char c0, char r0, char c1, char r1) {
        return legalMove(Move.move(c0, r0, c1, r1));
    }

    /**
     * Return true iff player WHO can move, ignoring whether it is
     * that player's move and whether the game is over.
     */
    boolean canMove(PieceColor who) {
        int allSquares = _board.length;
        for (int count = 0; count < allSquares; count++) {
            if (get(count) == who) {
                for (int i = -2; i <= 2; i++) {
                    for (int j = -2; j <= 2; j++) {
                        if (get(neighbor(count, i, j)) == EMPTY) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Return the color of the player who has the next move.  The
     *  value is arbitrary if the game is over. */
    PieceColor whoseMove() {
        return _whoseMove;
    }

    /** Return total number of moves and passes since the last
     *  clear or the creation of the board. */
    int numMoves() {
        return _allMoves.size();
    }

    /** Return number of non-pass moves made in the current game since the
     *  last extend move added a piece to the board (or since the
     *  start of the game). Used to detect end-of-game. */
    int numJumps() {
        return _numJumps;
    }

    /** Assuming MOVE has the format "-" or "C0R0-C1R1", make the denoted
     *  move ("-" means "pass"). */
    void makeMove(String move) {
        if (move.equals("-")) {
            makeMove(Move.pass());
        } else {
            makeMove(Move.move(move.charAt(0), move.charAt(1), move.charAt(3),
                    move.charAt(4)));
        }
    }

    /** Perform the move C0R0-C1R1, or pass if C0 is '-'.  For moves
     *  other than pass, assumes that legalMove(C0, R0, C1, R1). */
    void makeMove(char c0, char r0, char c1, char r1) {
        if (c0 == '-') {
            makeMove(Move.pass());
        } else {
            makeMove(Move.move(c0, r0, c1, r1));
        }
    }

    /** Make the MOVE on this Board, assuming it is legal. */
    void makeMove(Move move) {
        if (!legalMove(move)) {
            throw error("Illegal move: %s", move);
        }
        startUndo();
        if (move.isPass()) {
            pass();
            return;
        }
        _allMoves.add(move);
        PieceColor opp = _whoseMove.opposite();
        PieceColor cur = whoseMove();
        int mPPos = move.toIndex();
        int movePos = move.fromIndex();
        if (move.isExtend()) {
            set(mPPos, whoseMove());
            incrPieces(whoseMove(), 1);
            for (int mCheck = -1; mCheck < 2; mCheck++) {
                for (int moveCheck2 = -1; moveCheck2 < 2; moveCheck2++) {
                    if (get(neighbor(mPPos, mCheck, moveCheck2)) == opp) {
                        set(neighbor(mPPos, mCheck, moveCheck2), cur);
                        incrPieces(whoseMove().opposite(), -1);
                        incrPieces(whoseMove(), 1);
                    }
                }
            }
            _numJumps = 0;
        } else if (move.isJump()) {
            _numJumps += 1;
            set(movePos, EMPTY);
            set(mPPos, whoseMove());
            for (int moveCheck = -1; moveCheck < 2; moveCheck++) {
                for (int moveCheck2 = -1; moveCheck2 < 2; moveCheck2++) {
                    if (get(neighbor(mPPos, moveCheck, moveCheck2)) == opp) {
                        set(neighbor(mPPos, moveCheck, moveCheck2), cur);
                        incrPieces(opp, -1);
                        incrPieces(whoseMove(), 1);
                    }
                }
            }
        }


        _winner = getWinner();
        _whoseMove = opp;
        announce();

    }

    /** Update to indicate that the current player passes, assuming it
     *  is legal to do so. Passing is undoable. */
    void pass() {
        assert !canMove(_whoseMove);
        _allMoves.add(null);
        if (!canMove(_whoseMove)) {
            startUndo();
            _whoseMove = _whoseMove.opposite();
            announce();
        }
    }

    /** Undo the last move. */
    void undo() {
        while (!(_undoSquares.empty())) {
            if (_undoSquares.peek() == null) {
                _undoSquares.pop();
                _numJumps = _undoSquares.pop();
                break;
            }
            Integer tempSquare = _undoSquares.pop();
            PieceColor tempPiece = _undoPieces.pop();
            incrPieces(get(tempSquare), -1);
            incrPieces(tempPiece, 1);
            unrecordedSet(tempSquare, tempPiece);
        }
        _totalMoves -= 1;
        _whoseMove = _whoseMove.opposite();
        _allMoves.remove(_allMoves.size() - 1);
        _winner = null;
        announce();
    }

    /** Indicate beginning of a move in the undo stack. See the
     * _undoSquares and _undoPieces instance variable comments for
     * details on how the beginning of moves are marked. */
    private void startUndo() {
        _undoSquares.push(_numJumps);
        _undoSquares.push(null);

    }

    /** Add an undo action for changing SQ on current board. */
    private void addUndo(int sq) {
        _undoPieces.push(get(sq));
        _undoSquares.push(sq);
    }

    /** Return true iff it is legal to place a block at C R. */
    boolean legalBlock(char c, char r) {
        if (get(c, r) == EMPTY && _allMoves.isEmpty()) {
            return true;
        }
        return false;
    }

    /** Return true iff it is legal to place a block at CR. */
    boolean legalBlock(String cr) {
        return legalBlock(cr.charAt(0), cr.charAt(1));
    }

    /** Set a block on the square C R and its reflections across the middle
     *  row and/or column, if that square is unoccupied and not
     *  in one of the corners. Has no effect if any of the squares is
     *  already occupied by a block.  It is an error to place a block on a
     *  piece. */
    void setBlock(char c, char r) {
        if (!legalBlock(c, r)) {
            throw error("illegal block placement");
        }
        char posBlockRow =  (char) ('1' + '7' - r);
        char posBlockCol = (char) ('a' + 'g' - c);

        if (!canMove(RED) && !canMove(BLUE)) {
            _winner = EMPTY;
        }
        unrecordedSet(c, r, BLOCKED);
        unrecordedSet(posBlockCol, r, BLOCKED);
        unrecordedSet(c, posBlockRow, BLOCKED);
        unrecordedSet(posBlockCol, posBlockRow, BLOCKED);

        announce();
    }

    /** Place a block at CR. */
    void setBlock(String cr) {
        setBlock(cr.charAt(0), cr.charAt(1));
    }

    /** Return total number of unblocked squares. */
    int totalOpen() {
        _totalOpen = 0;
        int numSpace = EXTENDED_SIDE * EXTENDED_SIDE;
        for (int i = 0; i < numSpace; i++) {
            if (get(i) == EMPTY) {
                _totalOpen += 1;
            }
        }
        return _totalOpen;
    }

    /** Return a list of all moves made since the last clear (or start of
     *  game). */
    List<Move> allMoves() {
        return new ArrayList<Move>();
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Board)) {
            return false;
        }
        Board other = (Board) obj;
        return Arrays.equals(_board, other._board);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_board);
    }

    /** Return a text depiction of the board.  If LEGEND, supply row and
     *  column numbers around the edges. */
    String toString(boolean legend) {
        Formatter out = new Formatter();
        for (char r = '7'; r >= '1'; r -= 1) {
            if (legend) {
                out.format("%c", r);
            }
            out.format(" ");
            for (char c = 'a'; c <= 'g'; c += 1) {
                switch (get(c, r)) {
                case RED:
                    out.format(" r");
                    break;
                case BLUE:
                    out.format(" b");
                    break;
                case BLOCKED:
                    out.format(" X");
                    break;
                case EMPTY:
                    out.format(" -");
                    break;
                default:
                    break;
                }
            }
            out.format("%n");
        }
        if (legend) {
            out.format("   a b c d e f g");
        }
        return out.toString();
    }

    /** Set my notifier to NOTIFY. */
    public void setNotifier(Consumer<Board> notify) {
        _notifier = notify;
        announce();
    }

    /** Take any action that has been set for a change in my state. */
    private void announce() {
        _notifier.accept(this);
    }

    /** A notifier that does nothing. */
    private static final Consumer<Board> NOP = (s) -> { };

    /** Use _notifier.accept(this) to announce changes to this board. */
    private Consumer<Board> _notifier;

    /** For reasons of efficiency in copying the board,
     *  we use a 1D array to represent it, using the usual access
     *  algorithm: row r, column c => index(r, c).
     *
     *  Next, instead of using a 7x7 board, we use an 11x11 board in
     *  which the outer two rows and columns are blocks, and
     *  row 2, column 2 actually represents row 0, column 0
     *  of the real board.  As a result of this trick, there is no
     *  need to special-case being near the edge: we don't move
     *  off the edge because it looks blocked.
     *
     *  Using characters as indices, it follows that if 'a' <= c <= 'g'
     *  and '1' <= r <= '7', then row r, column c of the board corresponds
     *  to _board[(c -'a' + 2) + 11 (r - '1' + 2) ]. */
    private final PieceColor[] _board;

    /** Player that is next to move. */
    private PieceColor _whoseMove;

    /** Number of consecutive non-extending moves since the
     *  last clear or the beginning of the game. */
    private int _numJumps;

    /** Total number of unblocked squares. */
    private int _totalOpen;

    /** Number of blue and red pieces, indexed by the ordinal positions of
     *  enumerals BLUE and RED. */
    private int[] _numPieces = new int[BLUE.ordinal() + 1];

    /** Set to winner when game ends (EMPTY if tie).  Otherwise is null. */
    private PieceColor _winner;

    /** List of all (non-undone) moves since the last clear or beginning of
     *  the game. */
    private ArrayList<Move> _allMoves;

    /* The undo stack. We keep a stack of squares that have changed and
     * their previous contents.  Any given move may involve several such
     * changes, so we mark the start of the changes for each move (including
     * passes) with a null. */

    /** Stack of linearized indices of squares that have been modified and
     *  not undone. Nulls mark the beginnings of full moves. */
    private Stack<Integer> _undoSquares;
    /** Stack of pieces formally at corresponding squares in _UNDOSQUARES. */
    private Stack<PieceColor> _undoPieces;
    /**Counts the number of consecutive passes the player has taken.*/
    private int _numPass;
    /**Counts the total number of moves on the current board.*/
    private int _totalMoves;
    /**Counts the number of RED pieces on the board.*/
    private int _numRed;
    /**Counts the number of BLUE pieces on the board.*/
    private int _numBlue;



}
