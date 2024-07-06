package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;


public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    String userName;
    String fileName;
    byte[] dataToSend;
    byte[] dataToReceive;
    ConcurrentHashMap<String,File> fileMap;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(byte[] message) {
        short opCodeMessage = (short)(message[0] & 0xff);
        //classifying the message by its opCode

        //RRQ
        if(opCodeMessage == 1){
            proccesRRQ(message);
            
        }
        //WRQ
        else if(opCodeMessage == 2){
            proccesWRQ(message);
        }

    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    } 

    public void fileMapping(){
        fileMap = new ConcurrentHashMap<>();
        Path dir = Paths.get("Flies");

        try{
            Files.walk(dir).filter(Files::isRegularFile).forEach(  
                file -> {fileMap.put(file.getFileName().toString(), file.toFile());});

            } catch (IOException e){
                e.printStackTrace();
            }

        }

    

    private void proccesRRQ(byte[] message){
        int erorr = 0;
        boolean FileExist = false;

        if(connections.connected(connectionId)){
            //converting the message to string
            String fileName = new String(message, 1, message.length - 1, StandardCharsets.UTF_8);
            //checking if the file exist
            if(fileMap.containsKey(fileName)){
                FileExist = true;
                try{
                    File file = fileMap.get(fileName);
                    dataToSend = Files.readAllBytes(file.toPath());
                    connections.send(connectionId, dataToSend);
                } catch (IOException e){

                }
            }

            
        }
        else{
            erorr = 6; //LOGRQ ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }
        if(!FileExist){
            erorr = 1; //FILE NOT FOUND ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }

        }

    private void proccesWRQ(byte[] message){
        



    private  byte[] errorClassify(int error){
        byte[] errorArray = new byte[5];
        errorArray[0] = 0;
        errorArray[1] = 5;
        errorArray[2] = 0;
        errorArray[3] = error;
        errorArray[4] = 0;
        return errorArray;
    }









    
}
