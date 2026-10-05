package co.icesi.cine.model;

public class Seat {

    private boolean reserved;
    private String owner;

    public Seat() {
        reserved = false;
        owner = null;
    }

    public boolean isReserved() {
        return reserved;
    }

    public void setReserved(boolean reserved) {
        this.reserved = reserved;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
