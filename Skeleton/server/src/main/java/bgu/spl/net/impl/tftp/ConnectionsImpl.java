package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionHandler;



public class ConnectionsImpl<T> implements Connections<T>{ 

    private ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionsMap;

    public ConnectionsImpl(){
        connectionsMap = new ConcurrentHashMap<>();
    }
    

    //changed to boolean
    @Override
    public boolean connect(int connectionId, BlockingConnectionHandler<T> handler) {
        if(connectionsMap.containsKey(connectionId)){
            return false;
        }
        else{
            connectionsMap.put(connectionId, handler);
            return true;
        }
        
    }

    @Override   
    public boolean send(int connectionId, T msg) {
        if(connectionsMap.containsKey(connectionId)){
            connectionsMap.get(connectionId).send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) {
        if(connectionsMap.containsKey(connectionId)){
            connectionsMap.remove(connectionId);
        }

        
    }
    @Override
    public boolean connected(int connectionId){
        return connectionsMap.containsKey(connectionId);
    }

    

    

}
