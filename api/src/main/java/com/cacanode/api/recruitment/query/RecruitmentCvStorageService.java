package com.cacanode.api.recruitment.query;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.common.storage.DocumentStorage;
import com.cacanode.api.common.storage.StoredDocument;
import com.cacanode.api.recruitment.model.RecruitmentApplicationCv;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CvStorageState;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationCvRepository;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import com.cacanode.api.recruitment.service.RecruitmentCvValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class RecruitmentCvStorageService {
    private final DocumentStorage storage;
    private final HiringQuotaApi quota;
    private final RecruitmentCvValidator validator;
    private final RecruitmentApplicationCvRepository cvRepository;
    private final RecruitmentApplicationRepository applicationRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    @Autowired(required=false) private com.cacanode.api.recruitment.service.RecruitmentCvAnalysisService cvAnalysis;

    public StagedCv stage(UUID tenantId, UUID jobId, MultipartFile file) {
        if (file.getSize()<=0 || file.getSize()>5L*1024*1024) {
            throw new com.cacanode.api.common.exception.custom.BadRequestException("Invalid CV file");
        }
        UUID cvId=UUID.randomUUID();
        HiringQuotaApi.Reservation reservation;
        try { reservation=quota.reserveStorage(tenantId,cvId,file.getSize()); }
        catch (HiringQuotaApi.HiringQuotaException exception) { throw new com.cacanode.api.common.exception.custom.ConflictException(exception.getMessage()); }
        String quarantine="recruitment/%s/cv/quarantine/%s".formatted(tenantId,cvId);
        try {
            byte[] raw=file.getBytes();
            storage.store(quarantine,raw,file.getContentType());
            var validated=validator.validateAndScan(file);
            return new StagedCv(cvId,tenantId,jobId,reservation.reservationId(),quarantine,validated);
        } catch (RuntimeException exception) {
            safeDelete(quarantine); safeRelease(tenantId,reservation.reservationId()); throw exception;
        } catch (java.io.IOException exception) {
            safeDelete(quarantine); safeRelease(tenantId,reservation.reservationId());
            throw new com.cacanode.api.common.exception.custom.BadRequestException("Invalid CV file");
        }
    }

    public void discard(StagedCv staged) {
        if (staged == null) return;
        safeDelete(staged.quarantineKey()); safeDelete(staged.promotedKey());
        safeRelease(staged.tenantId(),staged.reservationId());
    }

    public void promote(StagedCv staged, UUID applicationId) {
        String promoted="recruitment/%s/applications/%s/cv/%s".formatted(staged.tenantId(),applicationId,staged.cvId());
        staged.setPromotedKey(promoted);
        storage.store(promoted,staged.cv().bytes(),staged.cv().contentType());
        storage.delete(staged.quarantineKey());
        try { quota.commitStorage(staged.tenantId(),staged.reservationId(),staged.cv().bytes().length); }
        catch (HiringQuotaApi.HiringQuotaException exception) { discard(staged); throw new com.cacanode.api.common.exception.custom.ConflictException(exception.getMessage()); }
        var p=new MapSqlParameterSource().addValue("id",staged.cvId()).addValue("tenantId",staged.tenantId())
                .addValue("applicationId",applicationId).addValue("jobId",staged.jobId())
                .addValue("filename",staged.cv().filename()).addValue("contentType",staged.cv().contentType())
                .addValue("bytes",staged.cv().bytes().length).addValue("sha256",staged.cv().sha256())
                .addValue("quarantine",staged.quarantineKey()).addValue("promoted",promoted)
                .addValue("reservationId",staged.reservationId());
        jdbc.update("""
                INSERT INTO recruitment_application_cvs(id,tenant_id,application_id,job_id,original_filename,
                    content_type,byte_size,content_sha256,storage_state,quarantine_object_key,
                    promoted_object_key,storage_reservation_id,active)
                VALUES(:id,:tenantId,:applicationId,:jobId,:filename,:contentType,:bytes,:sha256,
                    'PROMOTED',:quarantine,:promoted,:reservationId,true)
                """,p);
        jdbc.update("UPDATE recruitment_applications SET cv_present=true,updated_at=NOW() WHERE tenant_id=:tenantId AND id=:applicationId",
                p);
        registerRollbackCleanup(staged);
    }

    @Transactional(readOnly = true)
    public Download download(UUID tenantId, UUID applicationId) {
        RecruitmentApplicationCv cv=cvRepository.findByTenantIdAndApplicationIdAndActiveTrue(tenantId,applicationId)
                .filter(value -> value.getStorageState()!=CvStorageState.DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Application CV was not found"));
        StoredDocument stored=storage.load(cv.getPromotedObjectKey());
        return new Download(cv.getOriginalFilename(),cv.getContentType(),stored.content());
    }

    @Transactional
    public void scheduleImmediateDeletion(UUID tenantId, UUID applicationId) {
        cvRepository.findActiveForUpdate(tenantId,applicationId).ifPresent(cv -> {
            cv.setStorageState(CvStorageState.DELETION_PENDING); cv.setRetainedUntil(now());
            cv.setDeletionNextAttemptAt(now()); cvRepository.save(cv);
        });
    }

    @Transactional
    public void retainTerminal(UUID tenantId, UUID applicationId) {
        cvRepository.findActiveForUpdate(tenantId,applicationId).ifPresent(cv -> {
            cv.setRetainedUntil(now().plusDays(180)); cv.setDeletionNextAttemptAt(cv.getRetainedUntil());
            cvRepository.save(cv);
        });
    }

    @Transactional
    public void deleteNow(UUID tenantId, UUID applicationId) {
        if(cvAnalysis!=null)cvAnalysis.cancel(tenantId,applicationId);
        cvRepository.findActiveForUpdate(tenantId,applicationId).ifPresent(this::deleteObject);
    }

    @Scheduled(fixedDelayString = "${app.recruitment.public.cv-cleanup-ms:60000}")
    @Transactional
    public void cleanup() {
        List<RecruitmentApplicationCv> batch=cvRepository.findCleanupBatch(
                Set.of(CvStorageState.PROMOTED,CvStorageState.DELETION_PENDING,CvStorageState.DELETION_FAILED),
                now(), PageRequest.of(0,50));
        for (RecruitmentApplicationCv cv:batch) {
            try { deleteObject(cv); }
            catch (RuntimeException exception) {
                int attempts=Math.min(10,cv.getDeletionAttempts()+1); cv.setDeletionAttempts(attempts);
                cv.setStorageState(CvStorageState.DELETION_FAILED);
                cv.setDeletionLastError(exception.getClass().getSimpleName()+": "+safeMessage(exception));
                cv.setDeletionNextAttemptAt(attempts>=10?null:now().plusMinutes(Math.min(1440,1L << Math.min(attempts,10))));
                cvRepository.save(cv);
                log.warn("Recruitment CV deletion failed cvId={} attempt={}",cv.getId(),attempts);
            }
        }
    }

    private void deleteObject(RecruitmentApplicationCv cv) {
        if (cv.getPromotedObjectKey()!=null) storage.delete(cv.getPromotedObjectKey());
        if (cv.getQuarantineObjectKey()!=null) storage.delete(cv.getQuarantineObjectKey());
        quota.releaseStorage(cv.getTenantId(),cv.getStorageReservationId());
        cv.setStorageState(CvStorageState.DELETED); cv.setActive(false); cv.setDeletedAt(now());
        cv.setDeletionNextAttemptAt(null); cv.setDeletionLastError(null); cvRepository.save(cv);
        applicationRepository.findByIdAndTenantId(cv.getApplicationId(),cv.getTenantId()).ifPresent(application -> {
            application.setCvPresent(false); applicationRepository.save(application);
        });
    }

    private void registerRollbackCleanup(StagedCv staged) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status!=STATUS_COMMITTED) discard(staged);
            }
        });
    }
    private void safeDelete(String key){if(key!=null)try{storage.delete(key);}catch(RuntimeException ignored){}}
    private void safeRelease(UUID tenantId,UUID reservation){try{quota.releaseStorage(tenantId,reservation);}catch(RuntimeException ignored){}}
    private LocalDateTime now(){return LocalDateTime.now(clock);}
    private static String safeMessage(Throwable value){String message=value.getMessage();return message==null?"failure":message.substring(0,Math.min(450,message.length()));}

    public record Download(String filename,String contentType,byte[] content) {}
    public static final class StagedCv {
        private final UUID cvId,tenantId,jobId,reservationId; private final String quarantineKey;
        private final RecruitmentCvValidator.ValidatedCv cv; private String promotedKey;
        StagedCv(UUID cvId,UUID tenantId,UUID jobId,UUID reservationId,String quarantineKey,RecruitmentCvValidator.ValidatedCv cv){this.cvId=cvId;this.tenantId=tenantId;this.jobId=jobId;this.reservationId=reservationId;this.quarantineKey=quarantineKey;this.cv=cv;}
        public UUID cvId(){return cvId;} public UUID tenantId(){return tenantId;} public UUID jobId(){return jobId;}
        public UUID reservationId(){return reservationId;} public String quarantineKey(){return quarantineKey;}
        public RecruitmentCvValidator.ValidatedCv cv(){return cv;} public String promotedKey(){return promotedKey;}
        void setPromotedKey(String value){promotedKey=value;}
    }
}
