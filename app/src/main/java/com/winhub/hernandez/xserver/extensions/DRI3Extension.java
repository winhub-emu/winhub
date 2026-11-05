package com.winhub.hernandez.xserver.extensions;

import android.util.Log;
import com.winhub.hernandez.renderer.GPUImage;

import static com.winhub.hernandez.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winhub.hernandez.core.Callback;
import com.winhub.hernandez.sysvshm.SysVSharedMemory;
import com.winhub.hernandez.xconnector.XConnectorEpoll;
import com.winhub.hernandez.xconnector.XInputStream;
import com.winhub.hernandez.xconnector.XOutputStream;
import com.winhub.hernandez.xconnector.XStreamLock;
import com.winhub.hernandez.xserver.Drawable;
import com.winhub.hernandez.xserver.Pixmap;
import com.winhub.hernandez.xserver.Window;
import com.winhub.hernandez.xserver.XClient;
import com.winhub.hernandez.xserver.XLock;
import com.winhub.hernandez.xserver.XServer;
import com.winhub.hernandez.xserver.errors.BadAlloc;
import com.winhub.hernandez.xserver.errors.BadDrawable;
import com.winhub.hernandez.xserver.errors.BadIdChoice;
import com.winhub.hernandez.xserver.errors.BadImplementation;
import com.winhub.hernandez.xserver.errors.BadWindow;
import com.winhub.hernandez.xserver.errors.XRequestError;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DRI3Extension implements Extension {
    public static final byte MAJOR_OPCODE = -102;
    private final Callback<Drawable> onDestroyDrawableListener = (drawable) -> {
        ByteBuffer data = drawable.getData();
        SysVSharedMemory.unmapSHMSegment(data, data.capacity());
    };

    private static abstract class ClientOpcodes {
        private static final byte QUERY_VERSION = 0;
        private static final byte OPEN = 1;
        private static final byte PIXMAP_FROM_BUFFER = 2;
        private static final byte GET_SUPPORTED_MODIFIERS = 6;
        private static final byte PIXMAP_FROM_BUFFERS = 7;
    }

    @Override
    public String getName() {
        return "DRI3";
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

    private void queryVersion(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        inputStream.skip(8);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(1);
            // DRI3 1.2 only with the opt-in server GLX (Fast OpenGL, GLX route): Mesa gates its
            // multibuffer path (x11_dri3_has_multibuffer) on DRI3 >= 1.2 AND Present >= 1.2.
            outputStream.writeInt(client.xServer.isServerGlxEnabled() ? 2 : 0);
            outputStream.writePad(16);
        }
    }

    // DRI3 1.2 GetSupportedModifiers (opt-in server GLX only). We advertise no explicit modifiers
    // (empty window+screen lists); the guest Mesa then uses its AHardwareBuffer PixmapFromBuffers
    // path (modifier 1255), which pixmapFromBuffers() already handles.
    private void getSupportedModifiers(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int windowId = inputStream.readInt();
        inputStream.skip(4); // depth (1) + bpp (1) + unused (2)

        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);   // reply length (no modifier data follows)
            outputStream.writeInt(0);   // num window modifiers
            outputStream.writeInt(0);   // num screen modifiers
            outputStream.writePad(16);
        }
    }

    private void open(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int drawableId = inputStream.readInt();
        inputStream.skip(4);

        Drawable drawable = client.xServer.drawableManager.getDrawable(drawableId);
        if (drawable == null) throw new BadDrawable(drawableId);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writePad(24);
        }
    }

    private void pixmapFromBuffer(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int pixmapId = inputStream.readInt();
        int windowId = inputStream.readInt();
        int size = inputStream.readInt();
        short width = inputStream.readShort();
        short height = inputStream.readShort();
        short stride = inputStream.readShort();
        byte depth = inputStream.readByte();
        inputStream.skip(1);

        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        Pixmap pixmap = client.xServer.pixmapManager.getPixmap(pixmapId);
        if (pixmap != null) throw new BadIdChoice(pixmapId);

        int fd = inputStream.getAncillaryFd();
        pixmapFromFd(client, pixmapId, width, height, stride, 0, depth, fd, size);
    }

    private void pixmapFromBuffers(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        Log.d("Dri3", "Received pixmap from buffers");
        int pixmapId = inputStream.readInt();
        Log.d("Dri3", "Read pixmap id " + pixmapId);
        int windowId = inputStream.readInt();
        Log.d("Dri3", "Read window id " + windowId);
        inputStream.skip(4);
        short width = inputStream.readShort();
        Log.d("Dri3", "Read width " + width);
        short height = inputStream.readShort();
        Log.d("Dri3", "Read height " + height);
        int stride = inputStream.readInt();
        Log.d("Dri3", "Read stride " + stride);
        int offset = inputStream.readInt();
        Log.d("Dri3", "Read offset " + offset);
        inputStream.skip(24);
        byte depth = inputStream.readByte();
        Log.d("Dri3", "Read depth " + depth);
        inputStream.skip(3);
        long modifiers = inputStream.readLong();
        Log.d("Dri3", "Read modifiers " + modifiers);
        
        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);
        Pixmap pixmap = client.xServer.pixmapManager.getPixmap(pixmapId);
        if (pixmap != null) throw new BadIdChoice(pixmapId);
        
        int fd = inputStream.getAncillaryFd();
        long size = (long)stride * height;

        if (modifiers == 1255) {
            Log.d("Dri3", "Creating pixmap from AHardwareBuffer");
            pixmapFromHardwareBuffer(client, pixmapId, width, height, depth, fd);
        }
        else if (modifiers == 1274) {
            Log.d("Dri3", "Creating pixmap from dmabuf filedescriptor"); 
            pixmapFromFd(client, pixmapId, width, height, stride, offset, depth, fd, size);
        }    
    }
    
    private void pixmapFromHardwareBuffer(XClient client, int pixmapId, short width, short height, byte depth, int fd) throws IOException, XRequestError {
        try {
            GPUImage gpuImage = new GPUImage(fd);
            // GPUImage(fd) now locks the buffer, so getStride() is valid (matches the SHM path's
            // stride-based width). Mark the drawable directScanout so the Vulkan renderer can
            // present the AHB directly instead of compositing a blank CPU buffer (-> black).
            // ERL bug report #6: use the buffer's real height (from AHardwareBuffer_describe),
            // mirroring how width already uses getStride() rather than the wire value.
            Drawable drawable = client.xServer.drawableManager.createDrawable(pixmapId, gpuImage.getStride(), gpuImage.getHeight(), depth);
            drawable.setTexture(gpuImage);
            drawable.setDirectScanout(true);
            // Register the client as owner so the pixmap (and its GPUImage/AHB or SHM mapping)
            // is released in freeResources() when the client disconnects. Without this, DRI3
            // pixmaps leaked whenever a client died without an explicit FreePixmap. Mirrors the
            // core PixmapRequests path. pixmapId was validated non-existent above, so createPixmap
            // is non-null here; the guard is defensive only.
            Pixmap pixmap = client.xServer.pixmapManager.createPixmap(drawable);
            if (pixmap != null) client.registerAsOwnerOfResource(pixmap);
        }
        finally {
            XConnectorEpoll.closeFd(fd);
        }   
    }

    private void pixmapFromFd(XClient client, int pixmapId, short width, short height, int stride, int offset, byte depth, int fd, long size)  throws IOException, XRequestError {
        try {
            ByteBuffer buffer = SysVSharedMemory.mapSHMSegment(fd, size, offset, true);
            if (buffer == null) throw new BadAlloc();
            
            short totalWidth = (short)(stride / 4);
            Drawable drawable = client.xServer.drawableManager.createDrawable(pixmapId, totalWidth, height, depth);
            drawable.setData(buffer);
            drawable.setTexture(null);
            drawable.setOnDestroyListener(onDestroyDrawableListener);
            // Register the client as owner so the pixmap (and its GPUImage/AHB or SHM mapping)
            // is released in freeResources() when the client disconnects. Without this, DRI3
            // pixmaps leaked whenever a client died without an explicit FreePixmap. Mirrors the
            // core PixmapRequests path. pixmapId was validated non-existent above, so createPixmap
            // is non-null here; the guard is defensive only.
            Pixmap pixmap = client.xServer.pixmapManager.createPixmap(drawable);
            if (pixmap != null) client.registerAsOwnerOfResource(pixmap);
        }
        finally {
            XConnectorEpoll.closeFd(fd);
        }
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int opcode = client.getRequestData();
        switch (opcode) {
            case ClientOpcodes.QUERY_VERSION :
                queryVersion(client, inputStream, outputStream);
                break;
            case ClientOpcodes.OPEN :
                try (XLock lock = client.xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
                    open(client, inputStream, outputStream);
                }
                break;
            case ClientOpcodes.PIXMAP_FROM_BUFFER:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.PIXMAP_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
                    pixmapFromBuffer(client, inputStream, outputStream);
                }
                break;
            case ClientOpcodes.GET_SUPPORTED_MODIFIERS:
                if (!client.xServer.isServerGlxEnabled()) throw new BadImplementation();
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    getSupportedModifiers(client, inputStream, outputStream);
                }
                break;
            case ClientOpcodes.PIXMAP_FROM_BUFFERS:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.PIXMAP_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
                    pixmapFromBuffers(client, inputStream, outputStream);
                }
                break;
            default:
                throw new BadImplementation();
        }
    }
}
