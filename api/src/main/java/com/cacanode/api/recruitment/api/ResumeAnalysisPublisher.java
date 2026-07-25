package com.cacanode.api.recruitment.api;

import com.cacanode.api.recruitment.api.event.ResumeAnalysisRequestedEvent;

public interface ResumeAnalysisPublisher {
    void publish(ResumeAnalysisRequestedEvent event);
}
