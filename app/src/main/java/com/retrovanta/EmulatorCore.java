package com.retrovanta;

/** Common contract for the independently implemented console cores. */
interface EmulatorCore {
    void load(byte[] image);
    int[] frame();
    void setButtons(int bits);
    int videoWidth();
    int videoHeight();
    String getCartridgeTitle();
}
