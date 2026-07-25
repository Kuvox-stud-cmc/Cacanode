package com.cacanode.api.recruitment.service;

import java.io.OutputStream;
import java.util.Optional;

public interface RecordingTransport {
    Recording startDualChannelMp3(String callSid,String statusCallbackUrl);
    Optional<Recording> findForCall(String callSid);
    void stop(String recordingSid);
    long downloadMp3(String recordingSid,OutputStream target,long maximumBytes);
    void delete(String recordingSid);
    boolean exists(String recordingSid);

    record Recording(String recordingSid,String status) {}
    class UncertainFailure extends RuntimeException {public UncertainFailure(String message,Throwable cause){super(message,cause);}}
    class DefiniteFailure extends RuntimeException {public DefiniteFailure(String message,Throwable cause){super(message,cause);}}
}
