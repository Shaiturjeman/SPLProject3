package bgu.spl.net.impl.tftp;


import java.nio.ByteBuffer;
import java.util.Arrays;
import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    

    private int length = 0; 
    private byte[] bytes = new byte[1 << 10]; //start with 1k
    private int packetBitSize = 512;
    private int finalValue = packetBitSize;
    boolean ZeroByte = false;


    @Override
    public byte[] decodeNextByte(byte nextByte) {
        Byte copyByte = nextByte;

        if(copyByte == 0 && length == 0){
            return null;
        }
        
        if(copyByte == 0 && ZeroByte ){
            byte[] message = messageArray();
            length = 0;
            ZeroByte = false;
            finalValue = packetBitSize;
            return message;
        }

        bytes[length] = copyByte;
        length++;
        Byte opCodeByte = bytes[0];

        //opCodes 1,2,5,7,8,9 (RRQ, WRQ, ERROR, LOGRQ,DELRQ,BCAST )
        if(opCodeByte == 1 || opCodeByte == 2 || opCodeByte == 5 || opCodeByte == 7 || opCodeByte == 8 || opCodeByte == 9){
            if(length == 2){
                ZeroByte = true;
            }
        }
        //opCodes 3 (DATA)
        else if(opCodeByte == 3 && length ==3){
            finalValue = bytesToShort(bytes, 1, 2) + 5;
        }

        //opCodes 4 (ACK)
        else if(opCodeByte == 4){
            finalValue =3;
        }

        //opCodes 6 or 10 (DIRQ, DISC)
        else if(opCodeByte == 6 || opCodeByte == 10){
            finalValue = 1;
        }

        if(length == finalValue){
            byte[] message = messageArray();
            length = 0;
            ZeroByte = false;
            finalValue = packetBitSize;
            return message;
        }
        else{
            return null;
        }

    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    public byte[] messageArray(){
        byte[] message = new byte[length];
        for(int i = 0; i < length; i++){
            message[i] = bytes[i];
        }
        return message;
    }

    private byte[] shortToBytes(short num){
        return new byte[]{(byte)(num >>8) , (byte)(num & 0xFF)};
    }

    private short bytesToShort(byte[] byteArray, int start, int end){
        return (short) ((((short)(byteArray[start]) & 0xFF) << 8 | (short)(byteArray[end] & 0xFF)));
        
    }
}