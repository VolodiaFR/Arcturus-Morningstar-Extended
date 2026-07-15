final class OutgoingFixture {
    void composeInternal() {
        response.appendInt(7);
        appendDetails();
        response.appendBoolean(true);
    }

    private void appendDetails() {
        response.appendString("name");
        response.appendShort(2);
    }
}
