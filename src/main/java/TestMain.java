import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

public class TestMain {
    public static void main(String[] args) throws AddressStringException {
        IPAddress ipAddress = new IPAddressString("192.168.1.0").getAddress().withoutPrefixLength();
        ipAddress.setPrefixLength(24).iterator().forEachRemaining(ip->{
            System.out.println(ip.toIPAddress().withoutPrefixLength());
        });;

        //ipAddress.setPrefixLength(24);
        System.out.println(ipAddress.contains(new IPAddressString("192.168.1.5").getAddress()));
    }
}
