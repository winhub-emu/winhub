/*
 * Ported from Pipetto-crypto/winlator (branch winlator_bionic),
 * app/src/main/java/com/winlator/cmod/xserver/extensions/XComposite.java.
 * Original work Copyright (c) 2023 BrunoSX and contributors; MIT License.
 * Adapted for com.winhub.hernandez: package rename; MAJOR_OPCODE -106->-107.
 * See THIRD-PARTY-LICENSES.md.
 */
package com.winhub.hernandez.xserver.extensions;

import android.util.Log;
import static com.winhub.hernandez.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winhub.hernandez.xconnector.XInputStream;
import com.winhub.hernandez.xconnector.XOutputStream;
import com.winhub.hernandez.xconnector.XStreamLock;
import com.winhub.hernandez.xserver.Window;
import com.winhub.hernandez.xserver.XClient;
import com.winhub.hernandez.xserver.XServer;
import com.winhub.hernandez.xserver.errors.BadAccess;
import com.winhub.hernandez.xserver.errors.BadAlloc;
import com.winhub.hernandez.xserver.errors.BadImplementation;
import com.winhub.hernandez.xserver.errors.BadMatch;
import com.winhub.hernandez.xserver.errors.BadValue;
import com.winhub.hernandez.xserver.errors.BadWindow;
import com.winhub.hernandez.xserver.errors.XRequestError;
import java.io.IOException;

public class XComposite implements Extension {
    public static final byte MAJOR_OPCODE = -107; // -106 used by GLXExtension in this tree
    private XServer xserver;
    
    private static abstract class ClientOpcodes {
        private static final byte QUERY_VERSION = 0;
        private static final byte REDIRECT_WINDOW = 1;
        private static final byte UNREDIRECT_WINDOW = 3;
    }
    
    private static abstract class UpdateType {
        private static final int AUTOMATIC = 0;
        private static final int MANUAL = 1;
    }
    
    public XComposite(XServer xserver) {
        this.xserver = xserver;
    }
    
    private void queryVersion(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        inputStream.skip(8);
        
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(0);
            outputStream.writeInt(1);
            outputStream.writePad(16);
        }
    }
    
    private void redirectWindow(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int windowId = inputStream.readInt();
        int updateType = inputStream.readByte() & 0xff;
        inputStream.skip(3);
        
        Window window = xserver.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);
        
        if (window == xserver.windowManager.rootWindow) throw new BadMatch();
        if (window.isCompositeRedirected() && updateType == UpdateType.MANUAL) throw new BadAccess();
        
        window.setCompositeRedirected(true);
    }
    
    private void unredirectWindow(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int windowId = inputStream.readInt();
        int updateType = inputStream.readByte() & 0xff;
        inputStream.skip(3);
        
        Window window = xserver.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);
        
        if (!window.isCompositeRedirected()) throw new BadValue(windowId);
        
        window.setCompositeRedirected(false);
    }
    
    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int opcode = client.getRequestData();
        switch (opcode) {
            case ClientOpcodes.QUERY_VERSION:
                queryVersion(client, inputStream, outputStream);
                break;
            case ClientOpcodes.REDIRECT_WINDOW:
                redirectWindow(client, inputStream, outputStream);
                break;
            case ClientOpcodes.UNREDIRECT_WINDOW:
                unredirectWindow(client, inputStream, outputStream);
                break;
            default:
                throw new BadImplementation();
        }
    }
    
    @Override
    public String getName() {
        return "Composite";
    }
    
    @Override
    public byte getMajorOpcode() {
        return MAJOR_OPCODE;
    }

    @Override
    public byte getFirstErrorId() {
        return 0;
    }

    @Override
    public byte getFirstEventId() {
        return 0;
    }
}
