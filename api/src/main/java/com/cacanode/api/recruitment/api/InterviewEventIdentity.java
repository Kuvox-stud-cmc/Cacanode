package com.cacanode.api.recruitment.api;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class InterviewEventIdentity {
    public static final UUID NAMESPACE = UUID.fromString("95f2198b-9bb1-5895-87ce-324f54c90d63");

    private InterviewEventIdentity() {
    }

    public static UUID eventId(String eventType, UUID aggregateId, String semanticKey) {
        return uuidV5(NAMESPACE, eventType + "|" + aggregateId + "|" + semanticKey);
    }

    public static UUID runtimeEventId(String eventType,UUID sessionId,UUID callAttemptId,
            String semanticKey) {
        return uuidV5(NAMESPACE,eventType+"|"+sessionId+"|"+callAttemptId+"|"+semanticKey);
    }

    public static UUID resumeAnalysisId(UUID tenantId,UUID applicationId,String cvSha256,
            String mode,String policyVersion,String modelVersion) {
        return uuidV5(NAMESPACE,"cv-analysis|"+tenantId+"|"+applicationId+"|"+cvSha256+"|"
                +mode+"|"+policyVersion+"|"+modelVersion);
    }

    public static UUID resumeAnalysisId(UUID tenantId,UUID applicationId,UUID cvId,
            String cvSha256,String mode,String policyVersion,String modelVersion) {
        return uuidV5(NAMESPACE,"cv-analysis-v2|"+tenantId+"|"+applicationId+"|"+cvId+"|"
                +cvSha256+"|"+mode+"|"+policyVersion+"|"+modelVersion);
    }

    public static UUID resumeAnalysisId(UUID tenantId,UUID applicationId,UUID cvId,
            String cvSha256,String mode,String policyVersion,String modelVersion,int revision) {
        if(revision<1)throw new IllegalArgumentException("Analysis revision must be positive");
        return uuidV5(NAMESPACE,"cv-analysis-v1.2|"+tenantId+"|"+applicationId+"|"+cvId+"|"
                +cvSha256+"|"+mode+"|"+policyVersion+"|"+modelVersion+"|"+revision);
    }

    public static UUID turnId(UUID sessionId,int sequence) {
        if(sequence<1)throw new IllegalArgumentException("Turn sequence must be 1-based");
        return uuidV5(NAMESPACE,"interview.turn|"+sessionId+"|"+sequence+"|v1.1");
    }

    public static UUID runtimeTurnId(UUID sessionId,UUID callAttemptId,int sequence) {
        if(sequence<1)throw new IllegalArgumentException("Turn sequence must be 1-based");
        return uuidV5(NAMESPACE,"interview.turn|"+sessionId+"|"+callAttemptId+"|"+sequence+"|v1.2");
    }

    public static UUID uuidV5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            ByteBuffer namespaceBytes = ByteBuffer.allocate(16)
                    .putLong(namespace.getMostSignificantBits())
                    .putLong(namespace.getLeastSignificantBits());
            sha1.update(namespaceBytes.array());
            byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            ByteBuffer value = ByteBuffer.wrap(hash);
            return new UUID(value.getLong(), value.getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is required for UUIDv5", exception);
        }
    }
}
