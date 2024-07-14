package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TftpProtocol implements MessagingProtocol<byte[]> {
    

    //Opcode definitions 
    private static final byte READ_REQUEST = 1;
    private static final byte WRITE_REQUEST = 2;
    private static final byte DIR_REQUEST = 6;
    private static final byte LOGIN_REQUEST = 7;
    private static final byte DELETE_REQUEST = 8;
    private static final byte DISCONNECT_REQUEST = 10;

    private Map<String, File> fileMap = new HashMap<>(); //Map to store files
    private boolean terminateFlag = false; //flag to indiccate
    private boolean awaitingDirq = false;
    private boolean awaitingData = false;
    private boolean awaitingUpload = false;
    private static final int DATA_PACKET_SIZE = 512;
    private String uploadFileName = "";
    private String downloadFileName = "";
    private int currentBlockNum = 1;
    private byte[] dirqBuffer = new byte[0];
    private byte[] fileToTransmit;
    private List<Byte> fileContentBuffer = new ArrayList<>();

    @Override
    public byte[] process(byte[] msg) {
        short opcode = (short) (msg[0] & 0xff);
        int packetDataSize = 0;

        if (msg.length > 2) {
            packetDataSize = bytesToShort(msg, 1, 2);
        }

        switch (opcode) {
            case 3: // Data
                if (awaitingDirq) {
                    dirqBuffer = appendData(dirqBuffer, msg);
                    if (packetDataSize < DATA_PACKET_SIZE) {
                        displayDirq(msg);
                        awaitingDirq = false;
                        dirqBuffer = new byte[0];
                    }
                    return null;
                } else {
                    int blockNumber = bytesToShort(msg, 3, 4);
                    saveDataToFile(msg, blockNumber);
                    return generateAck((short) blockNumber);
                }
            case 4: // ACK
                int ackNumber = bytesToShort(msg, 1, 2);
                currentBlockNum = ackNumber + 1;
                if (awaitingUpload) {
                    return transmitFile(currentBlockNum, DATA_PACKET_SIZE * ackNumber);
                } else {
                    resetUploadState();
                }
                return null;
            case 5: // Error
                handleErrors(msg);
                return null;
            case 9: // Broadcast
                handleBroadcast(msg);
                return null;
            case 10: // Disconnect
                terminateFlag = true;
                return null;
            default:
                return null;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return terminateFlag;
    }

    public byte[] createRequest(String message) {
        String[] parts = splitMessage(message);
        byte opcode = determineOpcode(parts);
        byte[] requestData = encodeRequestData(parts, opcode);

        if (opcode != 0) {
            if (opcode == READ_REQUEST || opcode == WRITE_REQUEST || opcode == LOGIN_REQUEST || opcode == DELETE_REQUEST) {
                return combineOpcodeAndData(opcode, requestData);
            } else { // DIRQ or DISC
                return new byte[]{0, opcode};
            }
        } else {
            return null;
        }
    }

    private void handleErrors(byte[] msg) {
        String errorMessage = new String(msg, 3, msg.length - 4, StandardCharsets.UTF_8);
        System.out.println("Error: " + msg[2] + " " + errorMessage);

        awaitingDirq = false;
        if (awaitingUpload) {
            new File("." + File.separator + uploadFileName).delete();
        }
        resetUploadState();

        if (awaitingData) {
            new File("." + File.separator + downloadFileName).delete();
        }
        awaitingData = false;
        downloadFileName = "";
        uploadFileName = "";
    }

    private void handleBroadcast(byte[] msg) {
        short actionType = (short) (msg[1] & 0xff);
        String fileName = new String(msg, 2, msg.length - 2, StandardCharsets.UTF_8);
        if (actionType == 0) {
            System.out.println("BCAST: del " + fileName);
        } else {
            System.out.println("BCAST: add " + fileName);
        }
    }

    private byte determineOpcode(String[] parts) {
        if (parts.length == 1) {
            switch (parts[0]) {
                case "DIRQ":
                    awaitingDirq = true;
                    return DIR_REQUEST;
                case "DISC":
                    return DISCONNECT_REQUEST;
                default:
                    displayError(0);
            }
        } else if (parts.length == 2) {
            String command = parts[0];
            String argument = parts[1];
            switch (command) {
                case "RRQ":
                    if (!isFileExists(argument)) {
                        downloadFileName = argument;
                        awaitingData = true;
                        createNewFile(argument);
                        return READ_REQUEST;
                    } else {
                        displayError(5);
                    }
                    break;
                case "WRQ":
                    if (isFileExists(argument)) {
                        uploadFileName = argument;
                        awaitingUpload = true;
                        return WRITE_REQUEST;
                    } else {
                        displayError(1);
                    }
                    break;
                case "LOGRQ":
                    return LOGIN_REQUEST;
                case "DELRQ":
                    return DELETE_REQUEST;
                default:
                    displayError(0);
            }
        } else {
            displayError(0);
        }
        return 0;
    }

    private byte[] encodeRequestData(String[] parts, byte opcode) {
        if (parts.length == 2) {
            return parts[1].getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }

    private byte[] combineOpcodeAndData(byte opcode, byte[] data) {
        byte[] messageBytes = new byte[data.length + 3];
        messageBytes[0] = 0;
        messageBytes[1] = opcode;
        System.arraycopy(data, 0, messageBytes, 2, data.length);
        messageBytes[messageBytes.length - 1] = 0;
        return messageBytes;
    }

    private void createNewFile(String fileName) {
        File file = new File("." + File.separator + fileName);
        try {
            if (!file.createNewFile()) {
                displayError(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isFileExists(String fileName) {
        return new File("." + File.separator + fileName).exists();
    }

    private void saveDataToFile(byte[] msg, int blockNum) {
        Path filePath = Paths.get("." + File.separator, downloadFileName);

        for (int i = 5; i < msg.length; i++) {
            fileContentBuffer.add(msg[i]);
        }

        if (msg.length < DATA_PACKET_SIZE) {
            try {
                byte[] fileBytes = new byte[fileContentBuffer.size()];
                for (int i = 0; i < fileContentBuffer.size(); i++) {
                    fileBytes[i] = fileContentBuffer.get(i);
                }
                Files.write(filePath, fileBytes);
                fileContentBuffer.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("RRQ " + downloadFileName + " Complete");
            resetDownloadState();
        }
    }

    private void resetDownloadState() {
        awaitingData = false;
        downloadFileName = "";
        currentBlockNum = 0;
    }

    private void resetUploadState() {
        awaitingUpload = false;
        currentBlockNum = 1;
        uploadFileName = "";
        fileToTransmit = new byte[0];
    }

    private byte[] transmitFile(int blockNum, int startIndex) {
        Path filePath = Paths.get("." + File.separator, uploadFileName);
        if (Files.exists(filePath)) {
            try {
                fileToTransmit = Files.readAllBytes(filePath);
                byte[] dataPacket = generateDataPacket(blockNum, fileToTransmit, startIndex);
                if (dataPacket.length <= DATA_PACKET_SIZE + 6) {
                    resetUploadState();
                    System.out.println("WRQ " + uploadFileName + " complete");
                }
                return dataPacket;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            displayError(1);
        }
        return null;
    }

    private byte[] generateDataPacket(int blockNum, byte[] data, int startIndex) {
        int dataSize = Math.min(DATA_PACKET_SIZE, data.length - startIndex);
        byte[] dataPacket = new byte[dataSize + 6];
        dataPacket[0] = 0;
        dataPacket[1] = 3;
        byte[] blockNumberBytes = shortToBytes((short) blockNum);
        dataPacket[2] = blockNumberBytes[0];
        dataPacket[3] = blockNumberBytes[1];
        System.arraycopy(data, startIndex, dataPacket, 4, dataSize);
        return dataPacket;
    }

    private byte[] generateAck(short blockNum) {
        byte[] ack = new byte[4];
        ack[0] = 0;
        ack[1] = 4;
        byte[] blockNumBytes = shortToBytes(blockNum);
        ack[2] = blockNumBytes[0];
        ack[3] = blockNumBytes[1];
        return ack;
    }

    private byte[] appendData(byte[] existingData, byte[] newData) {
        byte[] combinedData = new byte[existingData.length + newData.length];
        System.arraycopy(existingData, 0, combinedData, 0, existingData.length);
        System.arraycopy(newData, 0, combinedData, existingData.length, newData.length);
        return combinedData;
    }

    private void displayDirq(byte[] msg) {
        int lastIndex = 5;
        while (lastIndex < msg.length) {
            int startIndex = lastIndex;
            while (lastIndex < msg.length && (msg[lastIndex] & 0xff) != 0) {
                lastIndex++;
            }
            String fileName = new String(msg, startIndex, lastIndex - startIndex, StandardCharsets.UTF_8);
            System.out.println(fileName);
            lastIndex++;
        }
    }

    private void displayError(int errorCode) {
        String[] errorMessages = {
                "Not defined, see error message (if any).",
                "File does not exist.",
                "Access violation.",
                "Disk full or allocation exceeded.",
                "Illegal TFTP operation.",
                "File already exists",
                "User not logged in",
                "User already logged in"
        };
        if (errorCode >= 0 && errorCode < errorMessages.length) {
            System.out.println("Error: " + errorMessages[errorCode]);
        }
    }

    private byte[] shortToBytes(short value) {
        return new byte[]{(byte) (value >> 8), (byte) (value & 0xff)};
    }

    private short bytesToShort(byte[] byteArray, int startIndex, int endIndex) {
        return (short) ((byteArray[startIndex] & 0xff) << 8 | (byteArray[endIndex] & 0xff));
    }

    private String[] splitMessage(String message) {
        int spaceIndex = message.indexOf(' ');
        if (spaceIndex == -1) {
            return new String[]{message};
        } else {
            return new String[]{message.substring(0, spaceIndex), message.substring(spaceIndex + 1)};
        }
    }
}
