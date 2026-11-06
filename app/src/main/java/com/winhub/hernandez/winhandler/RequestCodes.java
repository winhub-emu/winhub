package com.winhub.hernandez.winhandler;

abstract class RequestCodes {
    public static final byte EXIT = 0;
    public static final byte INIT = 1;
    public static final byte EXEC = 2;
    public static final byte KILL_PROCESS = 3;
    public static final byte LIST_PROCESSES = 4;
    public static final byte GET_PROCESS = 5;
    public static final byte SET_PROCESS_AFFINITY = 6;
    public static final byte MOUSE_EVENT = 7;
    public static final byte GET_GAMEPAD = 8;
    public static final byte GET_GAMEPAD_STATE = 9;
    public static final byte RELEASE_GAMEPAD = 10;
    public static final byte KEYBOARD_EVENT = 11;
    public static final byte BRING_TO_FRONT = 12;
    public static final byte CURSOR_POS_FEEDBACK = 13;
    /** HERMES-XInput (Direct XInput) rumble: "HXR1", slot u8, low/high-frequency motor u16 LE
     *  (raw XInput 0..65535), duration ms u16 LE; 12 bytes with the code. Sent by the guest DLL
     *  (app/src/main/cpp/hermes/xinput) only when the motors change, plus a refresh while running. */
    public static final byte XINPUT_RUMBLE = 14;
    /** HERMES-XInput status: "HXS1", requested mode u8, mode now u8, ok u8, control gen u32 LE,
     *  flags u32 LE, pid u32 LE, build u8; 21 bytes with the code. Sent when a control-block change
     *  (Direct / forward / hidden) has been applied in a process. */
    public static final byte XINPUT_STATUS = 16;
}