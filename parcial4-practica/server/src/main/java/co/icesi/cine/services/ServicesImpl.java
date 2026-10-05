package co.icesi.cine.services;

import co.icesi.cine.model.Seat;

public class ServicesImpl {

    public static final int ROWS = 5;
    public static final int COLS = 6;

    private Seat[][] seats;

    public ServicesImpl() {
        seats = new Seat[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                seats[i][j] = new Seat();
            }
        }
    }

    public Seat[][] getSeats() {
        return seats;
    }

    public int freeCount() {
        int free = 0;
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                free += seats[i][j].isReserved() ? 0 : 1;
            }
        }
        return free;
    }

    public Seat getSeat(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            throw new CinemaException("INVALID_SEAT");
        }
        return seats[row][col];
    }

    public void reserve(int row, int col, String user) {
        if (user == null || user.trim().isEmpty()) {
            throw new CinemaException("INVALID_DATA");
        }
        Seat seat = getSeat(row, col);
        if (seat.isReserved()) {
            throw new CinemaException("SEAT_TAKEN");
        }
        System.out.println("RESERVE (" + row + "," + col + ") -> " + user);
        seat.setReserved(true);
        seat.setOwner(user);
    }

    public void cancel(int row, int col, String user) {
        if (user == null || user.trim().isEmpty()) {
            throw new CinemaException("INVALID_DATA");
        }
        Seat seat = getSeat(row, col);
        if (!seat.isReserved() || !user.equals(seat.getOwner())) {
            throw new CinemaException("NOT_YOURS");
        }
        System.out.println("CANCEL (" + row + "," + col + ") <- " + user);
        seat.setReserved(false);
        seat.setOwner(null);
    }
}
