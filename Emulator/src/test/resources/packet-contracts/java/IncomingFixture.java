final class IncomingFixture {
    void handle() {
        int id = packet.readInt();
        readDetails();
        boolean enabled = packet.readBoolean();
    }

    private void readDetails() {
        String name = packet.readString();
        short count = packet.readShort();
    }
}
