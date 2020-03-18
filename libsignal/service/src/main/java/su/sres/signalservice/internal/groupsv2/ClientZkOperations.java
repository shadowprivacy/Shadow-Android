package su.sres.signalservice.internal.groupsv2;

import su.sres.zkgroup.ServerPublicParams;
import su.sres.zkgroup.profiles.ClientZkProfileOperations;

public final class ClientZkOperations {

    private final ClientZkProfileOperations clientZkProfileOperations;

    public ClientZkOperations(ServerPublicParams serverPublicParams) {
        clientZkProfileOperations = new ClientZkProfileOperations(serverPublicParams);
    }

    public ClientZkProfileOperations getProfileOperations() {
        return clientZkProfileOperations;
    }
}