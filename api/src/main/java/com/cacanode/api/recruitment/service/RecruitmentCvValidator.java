package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class RecruitmentCvValidator {
    public static final String PDF = "application/pdf";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final PublicRecruitmentProperties properties;
    private final RecruitmentMalwareScanner scanner;

    public ValidatedCv validateAndScan(MultipartFile file) {
        try {
            if (file == null || file.isEmpty() || file.getSize() > properties.maxCvBytes()) throw invalid();
            String name = file.getOriginalFilename();
            if (name == null || name.isBlank() || name.length() > 255) throw invalid();
            String lower = name.toLowerCase(Locale.ROOT);
            String declared = file.getContentType();
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > properties.maxCvBytes()) throw invalid();
            String type;
            if (lower.endsWith(".pdf") && PDF.equals(declared) && isPdf(bytes)) type=PDF;
            else if (lower.endsWith(".docx") && DOCX.equals(declared) && isDocx(bytes)) type=DOCX;
            else throw invalid();
            if (scanner.scan(bytes) != RecruitmentMalwareScanner.ScanResult.CLEAN) throw invalid();
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new ValidatedCv(name.replace('\r',' ').replace('\n',' '),type,bytes,hash);
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalid();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes.length >= 8 && bytes[0]=='%' && bytes[1]=='P' && bytes[2]=='D' && bytes[3]=='F' && bytes[4]=='-';
    }

    private static boolean isDocx(byte[] bytes) {
        if (bytes.length < 4 || bytes[0]!='P' || bytes[1]!='K') return false;
        Set<String> required = new HashSet<>(Set.of("[Content_Types].xml","_rels/.rels","word/document.xml"));
        long total=0; int entries=0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry=zip.getNextEntry())!=null;) {
                if (++entries > 1000 || entry.isDirectory()) continue;
                required.remove(entry.getName());
                long entryBytes=0; byte[] buffer=new byte[8192];
                for (int read; (read=zip.read(buffer))!=-1;) {
                    entryBytes+=read; total+=read;
                    if (entryBytes>10L*1024*1024 || total>20L*1024*1024) return false;
                }
                long compressed=entry.getCompressedSize();
                if (compressed>0 && entryBytes/compressed>100) return false;
            }
            return required.isEmpty();
        } catch (IOException exception) { return false; }
    }

    private static BadRequestException invalid() { return new BadRequestException("Invalid CV file"); }
    public record ValidatedCv(String filename,String contentType,byte[] bytes,String sha256) {}
}
