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
    static ConcurrentHashMap<Integer, String> LogedUsers = new ConcurrentHashMap<>();
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
            processRRQ(message);
            
        }
        //WRQ (Write Request)
        else if(opCodeMessage == 2){
            processWRQ(message);
        }
        //DATA (Data Packet)
        else if(opCodeMessage == 3){
            processDATAIn(message);
        }
        //ACK(ACK Packet)
        else if(opCodeMessage == 4){
            processACK(message);
        }
        //DIRQ
        else if(opCodeMessage == 6){
            processDIRQ(message);
        }
        //LOGRQ
        else if(opCodeMessage == 7){
            processLOGRQ(message);
        }
        //DELRQ
        else if(opCodeMessage == 8){
            processDELRQ(message);
        }
        //BCAST
        // else if(opCodeMessage == 9){
        //     processBCAST(message);
        // }
        //DISC
        else if(opCodeMessage == 10){
            processDISC(message);
        }


    }

    @Override
    public boolean shouldTerminate() {
        if(shouldTerminate){
            this.connections.disconnect(connectionId);
            LogedUsers.remove(connectionId);
            
        }
        return true;
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

    

    private void processRRQ(byte[] message){
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
                    byte[] packetToSend = createDataPacket(blockNumber, 0, dataToSend);
                    connections.send(connectionId, packetToSend);
                } catch (IOException e){
                    e.printStackTrace();

                }
            }

            
        }
        if(!FileExist){
            erorr = 1; //FILE NOT FOUND ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }
        else{
            erorr = 6; //LOGRQ ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }


        }

    private void processWRQ(byte[] message){
        int erorr = -1;
        //chekcing if the user is connected
        if(connections.connected(connectionId)){
            //converting the message to string
            String fileName = new String(message, 1, message.length - 1, StandardCharsets.UTF_8);
            //checking if the file exist
            if(fileMap.containsKey(fileName)){
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

    private void processDATAIn(byte[] message){
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
                processBCAST(((byte[])fileString.getBytes()), 1); // broadcasting that the file name  was added to the server, to all of the users
            } catch (IOException e){
                e.printStackTrace();
            }
        }




        }

    private void processACK(byte[] message){
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

            //data bytes (if the dataToSend array is bigger than 512 bytes, we will send only 512 bytes, otherwise we will send the rest of the data)
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

        // defining the errors 
        String Error0 = "Not defined, see error message (if any).";
        String Error1 = "File not found – RRQ DELRQ of non-existing file.";
        String Error2 = "Access violation - File cannot be written,read or deleted.";
        String Error3 = "Disk full or allocation exceeded - No room in disk.";
        String Error4 = "Illegal TFTP operation - Unknown Opcode.";
        String Error5 = "File already exists -File name exists on WRQ.";
        String Error6 = "User not logged in - Any opcode received before Login completes.";
        String Error7 = "User already logged in - Login username already connected.";
        
        //converting the error to bytes
        byte[] error0 = Error0.getBytes();
        byte[] error1 = Error1.getBytes();
        byte[] error2 = Error2.getBytes();
        byte[] error3 = Error3.getBytes();
        byte[] error4 = Error4.getBytes();
        byte[] error5 = Error5.getBytes();
        byte[] error6 = Error6.getBytes();
        byte[] error7 = Error7.getBytes();

        //preparing the errorPacket for sending
        byte[] errorPacket ;
        switch(error){
            case 0:
                errorPacket = new byte[error0.length + 5];
                for(int i =0; i< error0.length ; i++){
                    errorPacket[i=4] = error0[i];
                }
                break;
            case 1:
                errorPacket = new byte[error1.length + 5];
                for(int i =0; i< error1.length ; i++){
                    errorPacket[i=4] = error1[i];
                }
                break;
            case 2:
                errorPacket = new byte[error2.length + 5];
                for(int i =0; i< error2.length ; i++){
                    errorPacket[i=4] = error2[i];
                }
                break;
            case 3:
                errorPacket = new byte[error3.length + 5];
                for(int i =0; i< error3.length ; i++){
                    errorPacket[i=4] = error3[i];
            }
            break;
            case 4:
                errorPacket = new byte[error4.length + 5];
                for(int i =0; i< error4.length ; i++){
                    errorPacket[i=4] = error4[i];
                }
                break;
            case 5:
                errorPacket = new byte[error5.length + 5];
                for(int i =0; i< error5.length ; i++){
                    errorPacket[i=4] = error5[i];
                }
                break;
            case 6:
                errorPacket = new byte[error6.length + 5];
                for(int i =0; i< error6.length ; i++){
                    errorPacket[i=4] = error6[i];
                }
                break;
            case 7:
                errorPacket = new byte[error7.length + 5];
                for(int i =0; i< error7.length ; i++){
                    errorPacket[i=4] = error7[i];
                }
                break;
            default:
                errorPacket = new byte[error1.length + 5];
                for(int i =0; i< error0.length ; i++){
                    errorPacket[i=4] = error1[i];
                }
                break;


        }
        //compliting the error message by the format
        errorPacket[0] = (byte)0;
        errorPacket[1] = (byte)5;
        errorPacket[2] = shortToBytes((short)error)[0]; //accesing the first elemnt of the byte array returned by the function
        errorPacket[3] = shortToBytes((short)error)[1];
        errorPacket[errorPacket.length-1] = (byte)0;
        return errorPacket;
        




        

    }


    private void processDIRQ(byte[] message){
        int erorr = -1;
        //checking if the user is connected
        if(!connections.connected(connectionId)){
            erorr = 6; //LOGRQ ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }
        else{
            //creating a string that contains all of the files in the server
            String filesList = "";
            for(Map.Entry<String, File> entry : fileMap.entrySet()){
                //adding the file name to the string
                filesList += entry.getKey() + '\0';
            }
            //converting the string to bytes
            byte[] filesListBytes = filesList.getBytes();
            //sending the files list to the client
            byte[] packetToSend = createDataPacket(blockNumber, 0, filesListBytes);
            connections.send(connectionId, packetToSend);
        }
    }

    private void processLOGRQ(byte[] message){
        int erorr = -1;
        //checking if the user is connected
        if(connections.connected(connectionId)){
            erorr = 7; //USER ALREADY CONNECTED ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }

        else{
            //converting the message to string
            String userName = new String(message, 1, message.length - 1, StandardCharsets.UTF_8);
            //checking if the user name is already in the server
            if(LogedUsers.contains(userName)){
                erorr = 0; //user name already exist
                byte[] errorArray = errorClassify(erorr);
                connections.send(connectionId, errorArray);
            }
            else{
                LogedUsers.put(connectionId, userName);
                connections.send(connectionId, ACKout((short)0));
            }
        
        }
        
    }

    private void processDELRQ(byte[] message){
        int erorr = -1;
        //checking if the user is connected
        if(!connections.connected(connectionId)){
            erorr = 6; //LOGRQ ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }
        else{
            //converting the message to string
            String fileName = new String(message, 1, message.length - 1, StandardCharsets.UTF_8);
            //checking if the file exist
            if(fileMap.containsKey(fileName)){
                File file = fileMap.get(fileName);
                file.delete();
                fileMap.remove(fileName);
                processBCAST(fileName.getBytes(), 0);
            }
            else{
                erorr = 1; //FILE NOT FOUND ERROR
                byte[] errorArray = errorClassify(erorr);
                connections.send(connectionId, errorArray);
            }
        }
    }

    private void processBCAST(byte[] message,int delOrAdd){
        int erorr = -1;
        //checking if the user is connected
        if(!connections.connected(connectionId)){
            erorr = 6; //LOGRQ ERROR
            byte[] errorArray = errorClassify(erorr);
            connections.send(connectionId, errorArray);
        }
        else{
            byte[] broadMess = new byte[message.length + 4];
            broadMess[0] = (byte)0;
            broadMess[1] = (byte)9;
            broadMess[2] = (byte)delOrAdd;
            for(int i = 0; i< message.length; i++){
                broadMess[i+3] = message[i];
            }
            broadMess[broadMess.length -1] = (byte)0;
            for(int i = 0; i< LogedUsers.size(); i++){
                connections.send(i, broadMess);
            }

        }
    }

    private void processDISC(byte[] message){
        int error =-1;
        //checking if the user is connected
        if(!connections.connected(connectionId)){
            error = 6; //LOGRQ ERROR
            byte[] errorArray = errorClassify(error);
            connections.send(connectionId, errorArray);
        }
        else{
            shouldTerminate = true;
            connections.send(connectionId, ACKout((short)0));
            shouldTerminate();
        }
    }




        
        




    private byte[] shortToBytes(short num){
        return new byte[]{(byte)(num >>8) , (byte)(num & 0xFF)};
    }

    private short bytesToShort(byte[] byteArray, int start, int end){
        return (short) ((((short)(byteArray[start]) & 0xFF) << 8 | (short)(byteArray[end] & 0xFF)));
        
    }

    private byte[] createDataPacket(short blockNumber, int startIndex, byte[] inputData) {
        // Determine the size of the remaining data to send in the packet
        int remainingDataSize = Math.min(512, inputData.length - startIndex);
        byte[] packet = new byte[remainingDataSize + 6];
    
        packet[0] = (byte) 0;
        packet[1] = (byte) 3;
    
        byte[] sizeBytes = shortToBytes((short) remainingDataSize);
        packet[2] = sizeBytes[0];
        packet[3] = sizeBytes[1];
    
        byte[] blockNumBytes = shortToBytes(blockNumber);
        packet[4] = blockNumBytes[0];
        packet[5] = blockNumBytes[1];
    
        for (int i = 6; i < packet.length; i++) {
            packet[i] = inputData[startIndex + i - 6];
        }
        
        return packet;
    }
    

    
    











    
}
