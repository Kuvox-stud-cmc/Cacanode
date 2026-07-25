package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentCvValidatorTest {
    private final PublicRecruitmentProperties properties=new PublicRecruitmentProperties(
            "pepper","cursor","http://localhost/applications/manage",false,false,"","",
            false,"localhost",3310,5242880);

    @Test void acceptsPdfAndStructurallyValidDocx() throws Exception {
        RecruitmentCvValidator validator=new RecruitmentCvValidator(properties,content->RecruitmentMalwareScanner.ScanResult.CLEAN);
        assertEquals(RecruitmentCvValidator.PDF,validator.validateAndScan(new MockMultipartFile(
                "cv","resume.pdf",RecruitmentCvValidator.PDF,"%PDF-1.7\nbody".getBytes())).contentType());
        assertEquals(RecruitmentCvValidator.DOCX,validator.validateAndScan(new MockMultipartFile(
                "cv","resume.docx",RecruitmentCvValidator.DOCX,docx())).contentType());
    }

    @Test void rejectsMimeMagicArchiveAndMalwareFailures() throws Exception {
        RecruitmentCvValidator clean=new RecruitmentCvValidator(properties,content->RecruitmentMalwareScanner.ScanResult.CLEAN);
        assertThrows(BadRequestException.class,()->clean.validateAndScan(new MockMultipartFile(
                "cv","resume.pdf",RecruitmentCvValidator.PDF,"not a pdf".getBytes())));
        assertThrows(BadRequestException.class,()->clean.validateAndScan(new MockMultipartFile(
                "cv","resume.docx",RecruitmentCvValidator.DOCX,new byte[]{'P','K',3,4})));
        RecruitmentCvValidator infected=new RecruitmentCvValidator(properties,content->RecruitmentMalwareScanner.ScanResult.INFECTED);
        assertThrows(BadRequestException.class,()->infected.validateAndScan(new MockMultipartFile(
                "cv","resume.pdf",RecruitmentCvValidator.PDF,"%PDF-1.7\nEICAR".getBytes())));
    }

    private static byte[] docx() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            for(String name:new String[]{"[Content_Types].xml","_rels/.rels","word/document.xml"}){
                zip.putNextEntry(new ZipEntry(name));zip.write("<xml/>".getBytes());zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
