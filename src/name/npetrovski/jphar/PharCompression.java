package name.npetrovski.jphar;

public enum PharCompression {
    
    /**
     *  Value 	        Description
     *  ===================================================================================================
     *  0x00010000 	If set, this Phar contains a verification signature
     *  0x00001000 	If set, this Phar contains at least 1 file that is compressed with zlib compression
     *  0x00002000 	If set, this Phar contains at least 1 file that is compressed with bzip compression     
     */
    
    /**
     * File is compressed with zlib compression - 0x00001000.
     */
    GZIP(new byte[]{0, 0x10, 0, 0}),
    /**
     * File is compressed with bzip compression - 0x00002000.
     */
    BZIP2(new byte[]{0, 0x20, 0, 0}),
    /**
     * Phar is not compressed - 0x00000000.
     */
    NONE(new byte[]{0, 0, 0x00, 0});

    public final byte[] bitmapFlag;

    private PharCompression(final byte[] bitmapFlag) {
        this.bitmapFlag = bitmapFlag;
    }

    public byte[] getBitmapFlag() {
        return this.bitmapFlag;
    }

    public static PharCompression getEnumByInt(int code) {
        for (PharCompression e : PharCompression.values()) {
            int i = (e.bitmapFlag[3] << 24) & 0xff000000 | 
                    (e.bitmapFlag[2] << 16) & 0x00ff0000 | 
                    (e.bitmapFlag[1] << 8) & 0x0000ff00 | 
                    (e.bitmapFlag[0] << 0) & 0x000000ff;
            if (code == i) {
                return e;
            }
        }
        return null;
    }
}
