package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.BidiMessagingProtocol;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.Bidi;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final BidiMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, BidiMessagingProtocol<T> protocol) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read; // will be used to store the result of reading from the input stream

            in = new BufferedInputStream(sock.getInputStream());// to read form the input stream od the socket
            out = new BufferedOutputStream(sock.getOutputStream()); // to wrtie to the output stream of the socket

            
            // this continuos as long as the protocol does not indicate termination, the connection is active and there 
            // data to read from the input stream

            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {// this continuos as long as the protocol
                T nextMessage = encdec.decodeNextByte((byte) read); //decodes the next bytre from the input stream 
                if (nextMessage != null) {
                    protocol.process(nextMessage);  // if its not null its processed by the protocol
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(T msg) {
        try{
            if(msg !=null){
                out.write(encdec.encode(msg));
                out.flush();
            }

        }catch(IOException er){
            er.printStackTrace();
        }
    }
}
