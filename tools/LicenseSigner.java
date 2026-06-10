import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
public class LicenseSigner {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java LicenseSigner <private_key_b64> <name> <days>");
            System.exit(1);
        }
        byte[] privBytes = Base64.getDecoder().decode(args[0]);
        PrivateKey privKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privBytes));
        String payload = args[1] + "|" + LocalDate.now().plusDays(Long.parseLong(args[2]));
        byte[] payloadBytes = payload.getBytes("UTF-8");
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privKey);
        sig.update(payloadBytes);
        System.out.println(Base64.getEncoder().encodeToString(payloadBytes) + "." + Base64.getEncoder().encodeToString(sig.sign()));
    }
}