package bgu.spl.net.srv;

import java.io.IOException;

public interface Connections<T> {

    boolean connect(int connectionId, ConnectionHandler<T> handler);

    boolean send(int connectionId, T msg);

    void disconnect(int connectionId);

    boolean connected(int connectionId);
}
