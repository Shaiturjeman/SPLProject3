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
import java.util.List;
import java.util.ArrayList;


public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    byte[] dataToSend;
    List<Byte> dataToReceive;
    ConcurrentHashMap<String,File> fileMap;
    String fileString;
    short blockNumber =1;
    String userName;


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(byte[] message) {
        short opCodeMessage = (short)(message[0] & 0xff);
        //classifying the message by its opCode

        //RRQ (Read Request)
        if(opCodeMessage == 1){
            proccesRRQ(message);
            
        }
        //WRQ (Write Request)
        else if(opCodeMessage == 2){
            proccesWRQ(message);
        }
        //DATA (Data Packet)
        else if(opCodeMessage == 3){
            proccesDATAIn(message);
        }
        //ACK(ACK Packet)
        else if(opCodeMessage == 4){
            proccesACK(message);
        }
        //ERROR
        else if(opCodeMessage == 5){
            //proccesERROR(message);
        }
        //DIRQ
        else if(opCodeMessage == 6){
            //proccesDIRQ(message);
        }
        //LOGRQ
        else if(opCodeMessage == 7){
            //proccesLOGRQ(message);
        }
        //DELRQ
        else if(opCodeMessage == 8){
            //proccesDELRQ(message);
        }
        //BCAST
        else if(opCodeMessage == 9){
            //proccesBCAST(message);
        }
        //DISC
        else if(opCodeMessage == 10){
            //proccesDISC(message);
        }


    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    } 

    public TftpProtocol(){
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
        int erorr = -1;
        boolean FileExist = false;

        //chekcing if the user is connected
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
                    e.printStackTrace();

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
        int erorr = -1;
        boolean FileExist = false;


        //chekcing if the user is connected
        if(connections.connected(connectionId)){
            //converting the message to string
            String fileName = new String(message, 1, message.length - 1, StandardCharsets.UTF_8);
            //checking if the file exist
            if(fileMap.containsKey(fileName)){
                FileExist = true;
                erorr = 5; //FILE ALREADY EXIST ERROR, FILE NAME EXISTS IN THE SERVER
                byte[] errorArray = errorClassify(erorr);
                connections.send(connectionId, errorArray);
            }
            else{
                try{
                    File file = new File("Flies/" + fileName); // create a file object (not a file in the system yet, its a pathname object)
                    file.createNewFile(); // create a file in the system, if the file already exists, it will do nothing, but we've checked it earlier
                    // thats the file that going to be uploaded to the server
                    fileMap.put(fileName, file); // insert the file to the fileMap
                    fileString = fileName; // save the file name in a string inside the class field
                    connections.send(connectionId, ACKout((short)0)); // send an empty byte array to the client to tell him that the file is ready to be uploaded
                } catch (IOException e){
                    e.printStackTrace();

                }
            }

            
        }
        else{
            erorr = 6; //LOGRQ ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }

    }

    private void proccesDATAIn(byte[] message){
        int erorr = -1;

        //checking if the user is connected
        if(!connections.connected(connectionId)){
            erorr =6; //LOGRQ ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);

        }
        short blockNumber = bytesToShort(message, 3, 4);
        String folderPath = "Flies/" + fileString;
        Path path = fileMap.get(fileString).toPath();
        //extracting the data from the message
        dataToReceive = new ArrayList<Byte>();
        for(int i = 5; i < message.length; i++){ // starting from the 5th byte, because the first 5 bytes are the opCode, Packet Size and the block number
            dataToReceive.add(message[i]);
        }
        connections.send(connectionId, ACKout(blockNumber)); // sending an ACK to the client with the number of the block that was received
        if(message.length < 512){
            try{
                byte[] dataBytes = new byte[dataToReceive.size()];
                for(int i = 0; i < dataToReceive.size(); i++){
                    dataBytes[i] = dataToReceive.get(i);
                }
                dataToReceive.clear(); // clearing the dataToReceive list, so it will be ready for the next data packet
                Files.write(path, dataBytes); // writing the data to the file, if the file doesn't exist, it will create it
                File file = new File(folderPath);
                fileMap.put(fileString, file); // updating the file in the fileMap
                ProcessBCAST(fileString, 1); // broadcasting that the file name  was added to the server, to all of the users
            } catch (IOException e){
                e.printStackTrace();
            }
        }




        }

    private void proccesACK(byte[] message){
        int erorr = -1;
        if(!connections.connected(connectionId)){
            erorr = 6; //LOGRQ ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }
        short ACKBlockNumber = bytesToShort(message, 3, 4); // extracting the block number from the message
        blockNumber++; // incrementing the block number, in case the client will send another data packet
        if(ACKBlockNumber*512 >= dataToSend.length){
            dataToSend = null;
            blockNumber = 1;
        }
        else{
            byte[] packetToSend = dataToSend.length - ACKBlockNumber*512 > 512 ? new byte[516] : new byte[dataToSend.length - ACKBlockNumber*512 + 4];

            //opCode bytes
            packetToSend[0] = (byte)0;
            packetToSend[1] = (byte)3;

            //packet size bytes
            byte[] packetSize = shortToBytes(((short)(packetToSend.length - 4)));
            packetToSend[2] = packetSize[0];
            packetToSend[3] = packetSize[1];

            //block number bytes
            byte[] blockNumberBytes = shortToBytes(blockNumber);
            packetToSend[4] = blockNumberBytes[0];
            packetToSend[5] = blockNumberBytes[1];

            for(int i = 6; i < packetToSend.length; i++){
                packetToSend[i] = dataToSend[ACKBlockNumber*512 + i];
            }
            connections.send(connectionId, packetToSend);

            }
            



    }

    private byte[] ACKout(short blockNumber){
        byte[] ACK = new byte[4];
        //creating the ACK message
        ACK[0] = (byte)0; 
        ACK[1] = (byte)4;
        // converting the block number to bytes
        ACK[2] = shortToBytes(blockNumber)[0]; 
        ACK[3] = shortToBytes(blockNumber)[1];
        return ACK;
    }


        
        



    private  byte[] errorClassify(int error){
        byte[] errorArray = new byte[5];
        errorArray[0] = 0;
        errorArray[1] = 5;
        errorArray[2] = 0;
        errorArray[3] = error;
        errorArray[4] = 0;
        return errorArray;
    }

    private byte[] shortToBytes(short num){
        return new byte[]{(byte)(num >>8) , (byte)(num & 0xFF)};
    }

    private short bytesToShort(byte[] byteArray, int start, int end){
        return (short) ((((short)(byteArray[start]) & 0xFF) << 8 | (short)(byteArray[end] & 0xFF)));
        
    }









    
}
