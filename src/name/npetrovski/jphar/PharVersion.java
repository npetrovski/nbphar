package name.npetrovski.jphar;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;


public final class PharVersion {

    /**
     * Utility class. Can't instantiate.
     */
    private PharVersion() {
        throw new AssertionError();
    }

    public static String getVersion(byte[] b) {
        return "1.1.1";
    }
    
    public static byte[] getVersionNibbles(final String version) throws NumberFormatException {
        String[] splitted = version.split("\\.");
        if (splitted.length != 3) {
            throw new IllegalArgumentException("Version must contains 3 parts");
        }

        String[] hex = new String[4];

        for (int i = 0; i < splitted.length; i++) {
            int versionPartInt = Integer.parseInt(splitted[i]);
            if (versionPartInt < 0) {
                throw new NumberFormatException("Version cannot contains negative numbers");
            }
            if (versionPartInt > 15) {
                throw new NumberFormatException("Version cannot contains part over 15");
            }

            hex[i] = Integer.toHexString(versionPartInt);
        }

        hex[3] = "0"; // last nibble is not used

        HexBinaryAdapter adapter = new HexBinaryAdapter();

        byte[] nibbles = new byte[2];
        nibbles[0] = adapter.unmarshal(hex[0] + hex[1])[0];
        nibbles[1] = adapter.unmarshal(hex[2] + hex[3])[0];

        return nibbles;
    }

}
