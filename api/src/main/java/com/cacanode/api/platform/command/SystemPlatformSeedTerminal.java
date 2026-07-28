package com.cacanode.api.platform.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.IOException;

@Component
@ConditionalOnProperty(name = "app.command.mode", havingValue = "seed-platform-admin")
public class SystemPlatformSeedTerminal implements PlatformSeedTerminal {
    private final Console console = System.console();
    private final BufferedReader reader = console == null ? new BufferedReader(new InputStreamReader(System.in)) : null;

    @Override public boolean interactive() { return true; }

    @Override public String readLine(String prompt) {
        if (console != null) return console.readLine("%s", prompt);
        System.out.print(prompt);
        System.out.flush();
        try { return reader.readLine(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    @Override public char[] readPassword(String prompt) {
        if (console != null) return console.readPassword("%s", prompt);
        System.out.print(prompt);
        System.out.flush();
        try { String line = reader.readLine(); return line == null ? null : line.toCharArray(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    @Override public void print(String message) {
        if (console != null) { console.writer().println(message); console.writer().flush(); }
        else { System.out.println(message); System.out.flush(); }
    }
}
