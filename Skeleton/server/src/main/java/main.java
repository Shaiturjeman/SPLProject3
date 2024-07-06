import java.nio.ByteOrder;

public class main {

    public static void main(String[] args) {
        System.out.println("Hello, World!");
        ByteOrder byteOrder = ByteOrder.nativeOrder();
        if (byteOrder.equals(ByteOrder.BIG_ENDIAN)) {
            System.out.println("Big endian");
        } else {
            System.out.println("Little endian");
        }
    }
    }


