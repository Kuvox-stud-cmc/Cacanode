package com.cacanode.api.platform.command;

public interface PlatformSeedTerminal {
    boolean interactive();
    String readLine(String prompt);
    char[] readPassword(String prompt);
    void print(String message);
}
