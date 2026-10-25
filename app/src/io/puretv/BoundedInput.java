package io.puretv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bounds decoded response bytes as well as advertised Content-Length. The caller owns the stream. */
final class BoundedInput {
    private BoundedInput() {}
    static byte[] read(InputStream in,int limit)throws IOException {
        if(in==null || limit<1)throw new IOException("Invalid response stream");
        ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(limit,8192));
        byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){
            if(n>limit-out.size())throw new IOException("Response exceeds size limit");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
}
