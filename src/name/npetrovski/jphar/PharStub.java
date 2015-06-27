package name.npetrovski.jphar;

import java.io.IOException;

public final class PharStub implements PharWritable {

    static final String DEFAULT_STUB = "<?php\n__HALT_COMPILER(); ?>\n";

    private String stubCode = "";

    public PharStub() {
        this(DEFAULT_STUB);
    }

    public PharStub(String code) {
        setCode(code);
    }

    public void setCode(String code) {
        if (code.charAt(code.length() - 1) != '\r' && code.charAt(code.length() - 1) != '\n') {
            code = code + "\n";
        }

        this.stubCode = code;
    }
    
    public byte[] getStubCode() {
        return this.stubCode.getBytes();
    }
    
    public int getSize() {
        return this.stubCode.getBytes().length;
    }

    @Override
    public void write(final PharOutputStream out) throws IOException {
        if (this.stubCode != null) {
            out.writeString(this.stubCode);
        }
    }
}
